package dev.axkit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The app review's fixes: kept timeline, re-sent cards, read state, protocol fields, team-plan merge, first-look. */
class ReviewFixesTest {
    private val at = Instant.parse("2026-11-23T03:00:00Z")
    private fun msg(seq: Int, room: String? = null, text: String = "m$seq", role: String = "assistant") =
        AppEvent.Message(MessageEvent(seq = seq, at = at, role = role, text = text, channel = room))
    private fun card(seq: Int, id: String, room: String? = null, summary: String = "q") =
        AppEvent.Card(CardEvent(seq = seq, at = at, cardId = id, summary = summary, channel = room))

    @Test fun timelineSurvivesARestart() {
        val t = Timeline()
        t.apply(listOf(msg(1), msg(2, "dev"), card(3, "c1"), card(4, "c2"),
            AppEvent.CardClosed(CardClosedEvent(5, at, "c2", "approved")),
            AppEvent.Reaction(ReactionEvent(6, at, target = 1, emoji = "👍")), AppEvent.Unknown("future", 9)))
        t.closeLocally("c1", "denied")
        t.markRead("dev")
        val file = java.io.File.createTempFile("timeline", ".json")
        try {
            TimelineStore(file).save(t.snapshot())
            val back = TimelineStore(file).load()!!
            assertEquals(9, back.lastSeq)
            assertEquals(listOf(1, 3, 4), back.items("").map { it.seq })
            assertEquals("m2", (back.items("dev").single() as Timeline.Item.Message).e.text)
            assertEquals(0, back.unreadCount("dev"))
            assertFalse(back.isOpen("c1"))
            assertEquals("approved", back.closure("c2")?.state)
            assertEquals(mapOf("👍" to 1), back.reactions(1))
            // The stream carries on: an old seq is not taken twice, a new one is.
            assertFalse(back.apply(msg(2, "dev")))
            assertTrue(back.apply(msg(10)))
        } finally { file.delete() }
        assertNull(TimelineStore(java.io.File("/nonexistent/x.json")).load())
    }

    @Test fun reSentCardMovesToTheBottom() {
        val t = Timeline()
        t.apply(listOf(card(1, "c1", summary = "old"), msg(2)))
        t.apply(card(3, "c1", summary = "new"))
        assertEquals(listOf(2, 3), t.items("").map { it.seq })
        assertEquals("new", t.card("c1")?.summary)
    }

    @Test fun readSeqFromChannelsNeverMovesBack() {
        val t = Timeline()
        t.apply(listOf(msg(1, "dev"), msg(2, "dev"), msg(3, "dev")))
        t.setReadUpTo("dev", 2)
        assertEquals(1, t.unreadCount("dev"))
        t.setReadUpTo("dev", 1)
        assertEquals(2, t.readUpTo("dev"))
        val ch = ProtocolJson.decodeFromString(ChannelsView.serializer(), """{"channels":[{"id":"dev","label":"개발","readSeq":7,"unread":2}]}""")
        assertEquals(7, ch.channels[0].readSeq)
        assertEquals(2, ch.channels[0].unread)
    }

    @Test fun protocolFields() {
        val notice = EventDecoding.event("""{"type":"notice","seq":1,"at":"2026-11-23T03:00:00Z","text":"⚠️ 스미싱","kind":"phone.smishing"}""") as AppEvent.Notice
        assertTrue(notice.e.isUrgent)
        assertTrue((EventDecoding.event("""{"type":"notice","seq":2,"at":"2026-11-23T03:00:00Z","text":"[루틴] x","kind":"routines.result"}""") as AppEvent.Notice).e.isRoutineResult)
        val d = EventDecoding.event("""{"type":"delegation","seq":3,"at":"2026-11-23T03:00:00Z","bot":{"id":"b","name":"Mia"},"botChannel":"b","task":"t","author":{"id":"l","name":"Leo"}}""") as AppEvent.Delegation
        assertEquals("Leo", d.e.author?.name)
        val today = ProtocolJson.decodeFromString(TodayView.serializer(), """{"date":"2026-11-23","replies":[{"from":"민지","text":"내일 돼요?","source":"카톡"}],"spend":{"amount":12000,"count":2}}""")
        assertEquals("민지", today.replies!![0].from)
        assertEquals(2, today.spend!!.count)
        assertNull(ProtocolJson.decodeFromString(TodayView.serializer(), """{"date":"x","replies":null}""").replies)
    }

    @Test fun teamPlanKeepsUnknownFieldsAndToleratesMissingOnes() {
        val raw = """{"plan":{"id":"p1","status":"proposed","teams":[
            {"company":"오름","name":"웹","kind":"dev","color":"blue","roles":[{"title":"개발","does":"만들어요","seat":2}],
             "routines":[{"title":"점검","instruction":"확인해 줘","schedule":{"kind":"daily","at":"09:00"},"source":"mail"}]},
            {"name":"이름만"}]}}"""
        val view = TeamPlanView.decode(ProtocolJson.parseToJsonElement(raw))
        val teams = view.plan!!.teams
        assertEquals("", teams[1].company)
        var edited = TeamPlanEdit.update(teams.take(1), 0) { t -> t.copy(name = "웹사이트", roles = t.roles.map { it.copy(title = "프론트") }) }
        edited = TeamPlanEdit.update(edited, 0) { TeamPlanEdit.addRole(it) }
        val body = TeamPlanEdit.body(edited)["teams"]!!.jsonArray[0].jsonObject
        assertEquals("blue", body["color"]!!.jsonPrimitive.content)
        assertEquals("웹사이트", body["name"]!!.jsonPrimitive.content)
        val roles = body["roles"]!!.jsonArray.map { it.jsonObject }
        assertEquals("2", roles[0]["seat"]!!.jsonPrimitive.content)
        assertEquals("프론트", roles[0]["title"]!!.jsonPrimitive.content)
        assertNull(roles[1]["seat"])
        assertEquals("mail", body["routines"]!!.jsonArray[0].jsonObject["source"]!!.jsonPrimitive.content)
    }

    @Test fun firstLookDecodesAndSpeaksLikeTheTerminal() {
        val raw = """{"status":"running","phase":"projects","percent":42,"startedAt":"2026-11-23T03:00:00Z",
            "phases":[{"id":"local","label":"폴더 파일","done":120,"total":400,"state":"done"},
                      {"id":"projects","label":"프로젝트 문서·대화 기록","done":12,"total":40,"state":"active"},
                      {"id":"drive","label":"드라이브","done":0,"total":0,"state":"waiting"}],
            "result":{"about":"개발자 겸 운영자","companies":[{"name":"ACME","relation":"employer","projects":[
              {"name":"BetaLab","purpose":"학습 플랫폼","team":{"kind":"dev","roles":[{"title":"개발","does":"기능"}]},
               "routines":[{"title":"주간 점검","when":"매주 월 09:00","schedule":{"kind":"weekly","day":"mon","at":"09:00"}}]}]}],
             "plan":{"id":"p1","status":"proposed"}}}"""
        val v = ProtocolJson.decodeFromString(FirstLookView.serializer(), raw)
        assertTrue(v.running)
        assertEquals("프로젝트 문서·대화 기록 읽는 중", FirstLookWords.head(v))
        assertEquals("폴더 파일 120/400 · 프로젝트 문서·대화 기록 12/40", FirstLookWords.counts(v))
        assertEquals(0.42f, FirstLookWords.fraction(v), 0.001f)
        assertEquals("3분 12초", FirstLookWords.elapsedText(FirstLookWords.elapsed(v, Instant.parse("2026-11-23T03:03:12Z"))))
        val p = v.result!!.companies[0].projects[0]
        assertEquals("개발팀: 개발", FirstLookWords.team(p))
        assertEquals("매주 월 09:00", p.routines[0].whenText)
        assertTrue(v.result!!.plan!!.proposed)
        assertEquals("다니는 곳", FirstLookWords.relation("employer"))
        assertNull(FirstLookWords.startLabel(v))
        assertEquals("알아보기 계속", FirstLookWords.startLabel(v.copy(status = "paused")))
        assertEquals("알아보기 시작", FirstLookWords.startLabel(ProtocolJson.decodeFromString(FirstLookView.serializer(), "{}")))
    }
}

class WorkViewTest {
    @Test fun decodesAndSpeaksPlainly() {
        val raw = """{"title":"하고 있는 일","active":[{"id":"dev:job_1","kind":"dev","title":"owner/repo: 파서 고치기","status":"running","phase":"테스트 중",
            "startedAt":"2026-11-23T00:00:00.000Z","elapsedMinutes":12,"origin":{"channelId":"channel.app","address":"owner"}}],
            "recent":[{"id":"b1","kind":"browser","title":"가격","status":"done","summary":"마쳤어요","link":"https://example.com","elapsedMinutes":75},
                      {"id":"x","kind":"first-look","status":"interrupted"}]}"""
        val v = ProtocolJson.decodeFromString(WorkView.serializer(), raw)
        assertEquals("개발 · 테스트 중 · 12분째", WorkWords.line(v.active[0]))
        assertEquals("브라우저 심부름 · 1시간 15분 걸림", WorkWords.line(v.recent[0]))
        assertEquals("first-look", WorkWords.line(v.recent[1])) // unknown kinds are shown as they are
        assertEquals("중간에 멈춤", WorkWords.status("interrupted"))
        assertTrue(WorkWords.needsOwner(v.recent[1]))
    }

    @Test fun workResultNoticesNotifyAndCarryLive() {
        for (kind in listOf("dev.job", "browser.task", "errands.task", "work.interrupted")) {
            val n = (EventDecoding.event("""{"type":"notice","seq":1,"at":"2026-11-23T03:00:00Z","text":"끝","kind":"$kind"}""") as AppEvent.Notice).e
            assertTrue(kind, n.notifies)
            assertFalse(n.isUrgent)
        }
        val live = (EventDecoding.event("""{"type":"notice","seq":2,"at":"2026-11-23T03:00:00Z","text":"직접 해 주세요","kind":"browser.task","live":{"view":"browser-live","reason":"login"}}""") as AppEvent.Notice).e
        assertEquals("browser-live", live.live?.view)
        assertFalse((EventDecoding.event("""{"type":"notice","seq":3,"at":"2026-11-23T03:00:00Z","text":"x"}""") as AppEvent.Notice).e.notifies)
    }
}

class StreamLifecycleTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test fun aGoodAnswerResetsTheWaitAndStopClosesTheSocket() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            // Two quiet connections (200, nothing in them): each drop is the first one in a row again.
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": keepalive\n\n"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": keepalive\n\n"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setSocketPolicy(SocketPolicy.KEEP_OPEN))
            val api = ApiClient(ServerCredentials(server.url("/").toString().trimEnd('/'), "tok", "d", "n"))
            val states = Channel<StreamState>(Channel.UNLIMITED)
            val client = EventStreamClient(api)
            client.start(scope, lastSeq = { 0 }, onState = { states.send(it) }, onEvent = {})
            val waits = mutableListOf<StreamState.Waiting>()
            while (waits.size < 2) {
                val s = withTimeout(10_000) { states.receive() }
                if (s is StreamState.Waiting) { waits.add(s); client.reconnect() } // wake now instead of waiting 2 s
            }
            assertEquals(listOf(2L, 2L), waits.map { it.retryInSec })
            assertEquals(listOf(1, 1), waits.map { it.attempt })
            // Third connection stays open; stop() must close it (the server sees the request end).
            while (withTimeout(10_000) { states.receive() } != StreamState.Live) {}
            client.stop()
            assertEquals(3, server.requestCount)
        } finally { scope.cancel(); server.shutdown() }
    }
}
