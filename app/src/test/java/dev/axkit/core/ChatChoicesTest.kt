package dev.axkit.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Desktop 56cc0a6 · 07b5c12 · 46c81ae · 1187b97 · 062e1c0 on Android. */
class ChatChoicesTest {
    private val at = Instant.parse("2026-11-25T01:00:00Z")
    private val now = Instant.parse("2026-11-25T03:00:00Z")
    private val pick = listOf(Choice("a", "1번 답장 쓰기"), Choice("b", "내일 할게요"))

    private fun msg(seq: Int, text: String, owner: Boolean = false, choices: List<Choice>? = null, channel: String? = null, author: String? = null) =
        Timeline.Item.Message(MessageEvent(seq = seq, at = at, role = if (owner) "owner" else "assistant", text = text, choices = choices, channel = channel,
            author = author?.let { Author(it, it) }))
    private fun notice(seq: Int, text: String, choices: List<Choice>? = null, channel: String? = null) =
        Timeline.Item.Notice(NoticeEvent(seq = seq, at = at, text = text, choices = choices, channel = channel))

    @Test fun aNoticeThatAsksOrSaysMoreIsABubble() {
        assertTrue(ChatChoices.noticeIsBubble("저녁 정리예요", pick))
        assertTrue(ChatChoices.noticeIsBubble("첫 줄\n\n둘째 줄", null))
        assertFalse(ChatChoices.noticeIsBubble("  한 줄  \n", emptyList()))
    }

    @Test fun mainRoomNoticeWithButtonsIsTheAssistantSpeaking() {
        val rows = RoomFeed.build(listOf(notice(1, "답장할 메일이 2개 있어요", pick), notice(2, "백업을 마쳤어요")), "", null, "비서", now = now)
        val entries = rows.filterIsInstance<RoomFeed.Row.EntryRow>().map { it.entry }
        assertEquals(1, entries.size)
        assertEquals("비서", entries[0].speaker.name)
        assertEquals(1, rows.filterIsInstance<RoomFeed.Row.System>().size)
    }

    @Test fun onlyTheLatestButtonsWorkUntilTheOwnerSpeaks() {
        val items = listOf(notice(1, "고르세요", pick), msg(2, "다른 질문", choices = pick), msg(3, "그냥 말"))
        assertEquals(2, ChatChoices.liveSeq(items))
        assertNull(ChatChoices.liveSeq(items + msg(4, "1번 답장 쓰기", owner = true)))
        assertNull(ChatChoices.liveSeq(listOf(msg(1, "안녕"))))
    }

    private val card = CardEvent(seq = 5, at = at, cardId = "c1", summary = "보낼까요?",
        options = listOf(CardOption("send", "네, 보내요", "primary"), CardOption("no", "보내지 마세요", "danger")))

    @Test fun anAnsweredCardEchoesTheOwnersPick() {
        assertNull(ChatChoices.cardEcho(card, null))
        val yes = CardClosedEvent(seq = 6, at = at, cardId = "c1", state = "approved")
        assertEquals("네, 보내요", ChatChoices.cardEcho(card, yes))
        assertEquals("send", ChatChoices.answeredOption(card, yes))
        val no = CardClosedEvent(seq = 6, at = at, cardId = "c1", state = "denied")
        assertEquals("보내지 마세요", ChatChoices.cardEcho(card, no))
        val words = CardClosedEvent(seq = 6, at = at, cardId = "c1", state = "closed", answer = " 내일 보내 줘 ")
        assertEquals("내일 보내 줘", ChatChoices.cardEcho(card, words))
        assertNull(ChatChoices.answeredOption(card, words))
        assertNull(ChatChoices.cardEcho(card, CardClosedEvent(seq = 6, at = at, cardId = "c1", state = "expired")))
        val plain = CardEvent(seq = 7, at = at, cardId = "c2", summary = "지울까요?", effects = listOf("delete"))
        assertEquals("네, 지워요", ChatChoices.cardEcho(plain, CardClosedEvent(seq = 8, at = at, cardId = "c2", state = "approved")))
    }

    @Test fun teamAndBotRoomsShowEveryLineInOrder() {
        val items = (1..8).map { msg(it, "보고 $it", channel = "dev", author = "개발 팀장") } +
            listOf(notice(9, "💬 Leo → Mia · 검토 요청: 바꿔도 될까요?", channel = "dev"), notice(10, "↩️ Mia → Leo · 답 (승인): 좋아요.", channel = "dev"))
        val short = RoomFeed.build(items, "dev", null, "비서", now = now)
        assertTrue(short.any { it is RoomFeed.Row.FoldedRow })
        val all = RoomFeed.build(items, "dev", null, "비서", now = now, short = false)
        assertFalse(all.any { it is RoomFeed.Row.FoldedRow })
        val entries = all.filterIsInstance<RoomFeed.Row.EntryRow>().map { it.entry }
        assertEquals(10, entries.size)
        assertTrue(entries.all { it.replies.isEmpty() })
        assertEquals((1..10).toList(), entries.map { it.seq })
    }

    private fun bot(id: String, name: String, rank: String, team: String, role: String? = null, tier: String? = null, model: String? = null) =
        BotsView.Bot(id = id, name = name, role = role, channel = team, rank = rank, team = team, tier = tier, model = model)

    private val bots = BotsView(groups = listOf(
        BotsView.Group("hanbit", "한빛랩", bots = listOf(bot("dev", "개발 팀장", "lead", "dev"), bot("dev-1", "개발 직원 1", "staff", "dev", "코드를 고쳐요")),
            teams = listOf(BotsView.Team("dev", "개발팀", "앱을 고쳐요", "dev", members = listOf("dev", "dev-1")), BotsView.Team("design", "디자인팀", null, "design"))),
        BotsView.Group("moa", "모아카페", teams = listOf(BotsView.Team("stock", "재고팀", null, "stock"))),
    ))

    @Test fun renameTeamSendsPurposeOnlyWhenChanged() {
        val team = bots.allTeams[0]
        assertEquals(BotEdit.Result.Body(mapOf("teamId" to "dev", "name" to "앱 개발팀")), BotEdit.renameTeam(team, " 앱 개발팀 ", "앱을 고쳐요"))
        assertEquals(BotEdit.Result.Body(mapOf("teamId" to "dev", "name" to "개발팀", "purpose" to "서버도 고쳐요")), BotEdit.renameTeam(team, "개발팀", "서버도 고쳐요"))
        assertTrue(BotEdit.renameTeam(team, "  ", "") is BotEdit.Result.Problem)
        assertTrue(BotEdit.renameTeam(team, "가".repeat(41), "") is BotEdit.Result.Problem)
    }

    @Test fun editBotSendsOnlyWhatChanged() {
        val b = bots.bot("dev-1")!!
        assertNull(BotEdit.editBot(b, "개발 직원 1", "코드를 고쳐요"))
        assertEquals(BotEdit.Result.Body(mapOf("botId" to "dev-1", "role" to "테스트를 써요")), BotEdit.editBot(b, "개발 직원 1", "테스트를 써요"))
        assertTrue(BotEdit.editBot(b, "이름", "x") is BotEdit.Result.Problem)
        assertTrue(BotEdit.editBot(b, "", "") is BotEdit.Result.Problem)
    }

    @Test fun membersMoveToOtherTeamsButLeadsStay() {
        val from = bots.allTeams[0]
        assertEquals(emptyList<BotEdit.Target>(), BotEdit.moveTargets(bots, bots.bot("dev")!!, from))
        assertEquals(listOf("한빛랩 · 디자인팀", "모아카페 · 재고팀"), BotEdit.moveTargets(bots, bots.bot("dev-1")!!, from).map { it.label })
    }

    @Test fun modelSitsNextToTheTier() {
        assertEquals("등급 S · Claude Opus · 구독", BotEdit.modelLine(bot("a", "A", "lead", "t", tier = "S", model = "Claude Opus · 구독")))
        assertEquals("등급 B", BotEdit.modelLine(bot("a", "A", "lead", "t", tier = "B")))
        val team = listOf(bot("a", "A", "lead", "t", model = "Opus"), bot("b", "B", "staff", "t", model = "Opus"), bot("c", "C", "staff", "t", model = "Mini"), bot("d", "D", "staff", "t", model = "Flash"))
        assertEquals("Opus / Mini 외 1", BotEdit.teamModels(team))
        assertEquals("Opus", BotEdit.teamModels(team.take(2)))
        val decoded = ProtocolJson.decodeFromString(BotsView.Bot.serializer(), """{"id":"x","name":"X","channel":"c","tier":"A","model":"Claude Sonnet"}""")
        assertEquals("Claude Sonnet", decoded.model)
    }

    @Test fun teamFromTextChecksAndReads() {
        assertEquals("어떤 팀인지 조금만 더 적어 주세요.", TeamFromText.problem(" 팀 "))
        assertNull(TeamFromText.problem("유튜브 영상팀이 필요해요"))
        assertEquals("6,000자까지 적을 수 있어요.", TeamFromText.problem("가".repeat(6001)))
        val built = ProtocolJson.decodeFromString(TeamFromText.Built.serializer(),
            """{"teamId":"t1","channel":"t1","group":"moa","name":"영상 제작팀","purpose":"영상 2개","members":[{"id":"t1","name":"영상 제작 팀장","role":"이끌어요"}],"briefKept":true,"extra":1}""")
        assertTrue(built.isValid)
        assertEquals(listOf("영상 제작 팀장"), built.members.map { it.name })
        assertFalse(ProtocolJson.decodeFromString(TeamFromText.Built.serializer(), """{"name":"x"}""").isValid)
    }

    @Test fun aFileFromARoomCarriesItsChannel() = runBlocking {
        val server = MockWebServer()
        repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"ok":true,"seq":9}""")) }
        server.start()
        val client = ApiClient(ServerCredentials(server.url("").toString().trimEnd('/'), "t", "d", "폰"))
        client.share("hi".toByteArray(), "메모.txt", "text/plain", channel = "dev")
        assertEquals("dev", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("x-channel"))
        client.share("hi".toByteArray(), "메모.txt", "text/plain")
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("x-channel"))
        server.shutdown()
    }
}
