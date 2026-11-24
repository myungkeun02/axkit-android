package dev.axkit.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class SSEParserTest {
    @Test fun parsesEventIdAndDataAndSkipsComments() {
        val p = SSEParser()
        assertNull(p.feed(": open"))
        assertNull(p.feed("event: message"))
        assertNull(p.feed("id: 42"))
        assertNull(p.feed("data: {\"a\":1}"))
        assertEquals(SSEMessage("message", "42", "{\"a\":1}"), p.feed(""))
        assertNull("a blank line with nothing buffered yields nothing", p.feed(""))
    }

    @Test fun joinsMultiLineDataAndHandlesCR() {
        val p = SSEParser()
        p.feed("data: one\r"); p.feed("data:two")
        assertEquals("one\ntwo", p.feed("\r")?.data)
    }

    @Test fun backoffDoublesToSixtyAndResets() {
        val b = Backoff()
        assertEquals(listOf(2L, 4, 8, 16, 32, 60, 60), (0 until 7).map { b.fail() })
        b.reset()
        assertEquals(2L, b.fail())
    }
}

class EventDecodingTest {
    private fun decode(json: String) = EventDecoding.event(json)

    @Test fun messageWithChannelAndAuthor() {
        val e = decode("""{"type":"message","seq":3,"at":"2026-11-18T06:19:46.209Z","role":"assistant","text":"안녕","channel":"dev","author":{"id":"dev","name":"개발 봇"},"extra":1}""")
        val m = (e as AppEvent.Message).e
        assertEquals("안녕", m.text)
        assertEquals("개발 봇", m.author?.name)
        assertEquals("dev", e.channel)
    }

    @Test fun cardWithOptionsAndUnknownTone() {
        val e = decode("""{"type":"card","seq":5,"at":"2026-11-18T06:19:46Z","cardId":"c1","summary":"결제","effects":["pay"],"effectLabels":["결제"],"expiresAt":"2026-11-18T07:00:00Z","options":[{"id":"a","label":"승인","tone":"sparkly"}],"amount":{"value":12000,"currency":"KRW"}}""")
        val c = (e as AppEvent.Card).e
        assertEquals(CardOption.Tone.Neutral, c.options?.first()?.toneKind)
        assertTrue(c.isPayment)
        assertEquals(12000.0, c.amount?.value ?: 0.0, 0.0)
    }

    @Test fun unknownTypeAndBrokenKnownTypeBecomeUnknown() {
        assertEquals(AppEvent.Unknown("hologram", 9), decode("""{"type":"hologram","seq":9,"at":"x"}"""))
        assertEquals(AppEvent.Unknown("message", 10), decode("""{"type":"message","seq":10}"""))
        assertNull(decode("not json"))
    }

    @Test fun delegationDefaultsState() {
        val e = decode("""{"type":"delegation","seq":4,"at":"2026-11-18T06:19:46Z","bot":{"id":"dev","name":"개발 봇"},"botChannel":"dev","task":"로그인 고치기","state":"weird"}""")
        val d = (e as AppEvent.Delegation).e
        // iOS maps an unknown state to .started; Kotlin keeps the raw string. The room is "" either way (no channel).
        assertTrue(d.state == "started" || d.state == "weird")
        assertEquals("", e.channel)
    }

    @Test fun eventsArraySkipsGarbage() {
        val arr = ProtocolJson.parseToJsonElement("""[{"type":"notice","seq":1,"at":"2026-11-18T06:19:46Z","text":"hi"}, 5, {"nope":true}]""") as kotlinx.serialization.json.JsonArray
        assertEquals(listOf(1), EventDecoding.events(arr).map { it.seq })
    }
}

class PairingTest {
    @Test fun parsesLinkAndAccessToken() {
        val link = PairingLink.parse("axkit://pair?u=https%3A%2F%2Fmac.tail1.ts.net%2F&n=abc_123&c=482913&ci=id1&cs=sec")
        assertEquals("https://mac.tail1.ts.net", link.baseUrl)
        assertEquals("abc_123", link.nonce)
        assertEquals("482913", link.code)
        assertEquals("id1", link.accessClientId)
        assertEquals("sec", link.accessClientSecret)
    }

    @Test fun rejectsPublicHttpAndJunk() {
        val e = assertThrows(PairingLink.ParseException::class.java) { PairingLink.parse("axkit://pair?u=http%3A%2F%2Fexample.com&n=a&c=123456") }
        assertEquals(PairingLink.ParseError.InsecureAddress, e.reason)
        assertThrows(PairingLink.ParseException::class.java) { PairingLink.parse("https://pair?u=x") }
        assertThrows(PairingLink.ParseException::class.java) { PairingLink.parse("axkit://pair?u=https%3A%2F%2Fa.b&n=a%2F..&c=1") }
    }

    @Test fun privateHosts() {
        for (h in listOf("127.0.0.1", "10.1.2.3", "192.168.0.9", "172.20.1.1", "100.101.1.2", "localhost", "::1")) assertTrue(h, AddressPolicy.isPrivateHost(h))
        for (h in listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "mac.ts.net", "1.2.3")) assertFalse(h, AddressPolicy.isPrivateHost(h))
    }

    @Test fun displayHost() {
        assertEquals("mac.ts.net", ServerCredentials("https://mac.ts.net:8443", "t", "d", "n").displayHost)
    }
}

class TimelineTest {
    private val at: Instant = Instant.ofEpochSecond(1_800_000_000)

    private fun message(seq: Int, text: String, channel: String? = null, role: String = "assistant") =
        AppEvent.Message(MessageEvent(seq = seq, at = at, role = role, text = text, channel = channel))

    private fun card(seq: Int, id: String, expires: Instant? = null) =
        AppEvent.Card(CardEvent(seq = seq, at = at, cardId = id, summary = "s$seq", effects = listOf("pay"), effectLabels = listOf("결제"), expiresAt = expires))

    @Test fun splitsRoomsDedupesAndOrders() {
        val t = Timeline()
        t.apply(listOf(message(3, "c"), message(1, "a"), message(2, "b", channel = "dev")))
        assertFalse(t.apply(message(1, "again")))
        assertEquals(listOf(1, 3), t.items("").map { it.seq })
        assertEquals(listOf(2), t.items("dev").map { it.seq })
        assertEquals(3, t.lastSeq)
        t.apply(message(2, "late"))
        assertEquals(2, t.items("").size)
    }

    @Test fun cardsOpenCloseExpireAndReplaceInPlace() {
        val t = Timeline()
        t.apply(card(1, "a", at.plusSeconds(600)))
        t.apply(card(2, "b", at.minusSeconds(1)))
        assertEquals(listOf("a"), t.openCards(at).map { it.cardId })
        t.apply(AppEvent.Card(CardEvent(seq = 3, at = at, cardId = "a", summary = "new", expiresAt = at.plusSeconds(600))))
        assertEquals(2, t.items("").size)
        assertEquals("new", t.card("a")?.summary)
        t.apply(AppEvent.CardClosed(CardClosedEvent(seq = 4, at = at, cardId = "a", state = "approved")))
        assertTrue(t.openCards(at).isEmpty())
        assertEquals(setOf("a", "b"), t.finishedCards(at).map { it.cardId }.toSet())
    }

    @Test fun sameQuestionShowsOnce() {
        val t = Timeline()
        fun ask(seq: Int, id: String, summary: String = "Drive에 저장할까요?") =
            AppEvent.Card(CardEvent(seq = seq, at = at, cardId = id, summary = summary, effects = listOf("send"), note = "회의록", expiresAt = at.plusSeconds(600)))
        t.apply(ask(1, "a")); t.apply(ask(2, "b")); t.apply(ask(3, "c", "다른 질문"))
        assertEquals(listOf("c", "b"), t.openCards(at).map { it.cardId })
        assertTrue(t.isDuplicate("a", at))
        assertFalse(t.isDuplicate("b", at))
        val withTopic = CardEvent(seq = 4, at = at, cardId = "d", summary = "말만 다름", expiresAt = at.plusSeconds(600), topic = "drive:save")
        val again = withTopic.copy(seq = 5, cardId = "e", summary = "또 다른 말")
        t.apply(AppEvent.Card(withTopic)); t.apply(AppEvent.Card(again))
        assertEquals(listOf("e"), t.openCards(at).filter { it.topic == "drive:save" }.map { it.cardId })
    }

    @Test fun unreadCountsOnlyAssistantSideAndResetsOnRead() {
        val t = Timeline()
        t.apply(listOf(message(1, "me", role = "owner"), message(2, "bot"), card(3, "x")))
        assertEquals(2, t.unreadCount(""))
        t.markRead("")
        assertEquals(0, t.unreadCount(""))
        assertEquals("s3", t.preview("")?.first)
    }

    @Test fun defaultOptionsMapToDecisions() {
        val c = CardEvent(seq = 1, at = at, cardId = "c", summary = "", effects = listOf("send"))
        val options = CardWords.defaultOptions(c)
        assertEquals("네, 보내요", options.first().label)
        assertEquals(CardAnswer.Decision("approve"), CardWords.answer(options[0]))
        assertEquals(CardAnswer.Option("org:d:1"), CardWords.answer(CardOption("org:d:1", "x")))
        assertEquals("답함: 내일 해", CardWords.closedLabel(CardClosedEvent(seq = 1, at = at, cardId = "c", state = "closed", answer = "내일 해"), false))
    }
}

class OutboxTest {
    private val ok = SendResult(seq = 10)

    @Test fun persistsSendsInOrderAndStopsOnTransientFailure() = runBlocking {
        val file = java.io.File.createTempFile("outbox", ".json").also { it.delete() }
        try {
            val box = Outbox(file)
            val first = box.enqueue("하나", "")
            box.enqueue("둘", "dev")
            val reopened = Outbox(file)
            assertEquals(listOf("하나", "둘"), reopened.pending().map { it.text })

            val failing = reopened.flush { throw ApiError.Offline }
            assertEquals(2, failing.remaining)
            assertTrue(failing.retryLater)
            assertEquals(1, reopened.pending().first().attempts)

            val seen = mutableListOf<String>()
            val result = reopened.flush { seen.add(it.id); SendResult(seq = 20 + seen.size) }
            assertEquals(first.id, result.sent.first())
            assertEquals(0, result.remaining)
            // Sent ones stay (as plain bubbles) until their own line reaches the timeline.
            assertEquals(listOf("sent", "sent"), reopened.pending().map { it.state })
            assertFalse(reopened.confirm(20))
            assertTrue(reopened.confirm(21))
            assertEquals(1, reopened.pending().size)
            reopened.confirm(22)
            assertFalse(file.exists())
        } finally { file.delete() }
    }

    @Test fun refusedIsKeptAsFailedAndStaleCodesAreDropped() = runBlocking {
        var now = 0L
        val box = Outbox(null) { now }
        box.enqueue("인증번호 482913", ""); box.enqueue("too long", ""); box.enqueue("fine", "")
        now = 11 * 60 * 1000L
        val result = box.flush { if (it.text == "too long") throw ApiError.BadRequest("shape") else ok }
        assertEquals(1, result.dropped.size)
        assertEquals(1, result.failed.size)
        assertEquals(1, result.sent.size)
        val failed = box.pending().first { it.isFailed }
        assertEquals("too long", failed.text)
        assertEquals("보낸 내용을 맥이 이해하지 못했어요.", failed.problem)
        // 다시 보내기: back in line, sent this time.
        box.retry(failed.id)
        assertEquals(listOf(failed.id), box.flush { ok }.sent)
    }

    @Test fun unauthorizedKeepsEverythingAndBusyFailsAfterFiveTries() = runBlocking {
        val box = Outbox(null)
        box.enqueue("a", ""); box.enqueue("b", "")
        val r = box.flush { throw ApiError.Unauthorized }
        assertTrue(r.unauthorized)
        assertEquals(listOf("waiting", "waiting"), box.pending().map { it.state })
        repeat(Outbox.MAX_BUSY_ATTEMPTS - 1) { assertTrue(box.flush { m -> if (m.text == "a") throw ApiError.RateLimited else ok }.retryLater) }
        val last = box.flush { m -> if (m.text == "a") throw ApiError.Server(503, null) else ok }
        assertEquals(1, last.failed.size)
        assertEquals("보내지 못했어요", box.pending().first { it.text == "a" }.problem)
        assertEquals("sent", box.pending().first { it.text == "b" }.state)
    }

    @Test fun choicesAndSharedGoThroughWithTheirFields() = runBlocking {
        val box = Outbox(null)
        box.enqueue("네, 해 주세요", "dev", choice = "c1")
        box.enqueue("https://example.com", "", shared = true)
        val seen = mutableListOf<PendingMessage>()
        box.flush { seen.add(it); ok }
        assertEquals("c1", seen[0].choice)
        assertTrue(seen[1].shared)
    }

    @Test fun longTextIsSplitAtLineEnds() {
        val text = ("가".repeat(5000) + "\n") + "나".repeat(5000)
        val parts = Outbox.split(text)
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.length <= Outbox.MAX_TEXT })
        assertEquals("가".repeat(5000), parts[0])
        assertEquals(listOf("짧아요"), Outbox.split("짧아요"))
        assertEquals(listOf(2L, 4L, 8L, 16L, 30L, 30L), (1..6).map { Outbox.retryDelaySec(it) })
    }

    @Test fun looksLikeCode() {
        assertTrue(Outbox.looksLikeCode("인증번호는 [123456] 입니다"))
        assertTrue(Outbox.looksLikeCode("Your code: 9921"))
        assertFalse(Outbox.looksLikeCode("내일 3시에 만나요"))
        assertFalse(Outbox.looksLikeCode("코드 리뷰 해 줘"))
    }
}

class LayoutAndFormatTest {
    @Test fun layoutIsDeterministicFiniteAndPullsLinksTogether() {
        val nodes = (0 until 12).map { "n$it" }
        val links = listOf("n0" to "n1", "n1" to "n2", "n2" to "n0")
        val a = GraphLayout.layout(nodes, links, size = 1000.0, iterations = 200)
        assertEquals(a, GraphLayout.layout(nodes, links, size = 1000.0, iterations = 200))
        assertEquals(12, a.size)
        for (p in a.values) assertTrue(p.x.isFinite() && abs(p.x) <= 500.001 && abs(p.y) <= 500.001)
        fun d(x: String, y: String) = hypot(a[x]!!.x - a[y]!!.x, a[x]!!.y - a[y]!!.y)
        val linked = (d("n0", "n1") + d("n1", "n2") + d("n2", "n0")) / 3
        val others = nodes.drop(3).map { d("n0", it) }.sum() / 9
        assertTrue(linked < others)
        assertTrue(GraphLayout.layout(emptyList(), emptyList()).isEmpty())
    }

    @Test fun layout3DIsDeterministicInsideTheSphereAndPullsLinksTogether() {
        val nodes = (0 until 14).map { "n$it" }
        val links = listOf("n0" to "n1", "n1" to "n2", "n2" to "n0")
        val a = GraphLayout.layout3D(nodes, links, size = 1000.0, iterations = 150)
        assertEquals(a, GraphLayout.layout3D(nodes, links, size = 1000.0, iterations = 150))
        for (p in a.values) assertTrue(sqrt(p.x * p.x + p.y * p.y + p.z * p.z) <= 500.001)
        fun d(x: String, y: String): Double { val p = a[x]!!; val q = a[y]!!; return sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y) + (p.z - q.z) * (p.z - q.z)) }
        val linked = (d("n0", "n1") + d("n1", "n2") + d("n2", "n0")) / 3
        val others = nodes.drop(3).map { d("n0", it) }.sum() / 11
        assertTrue(linked < others)
    }

    @Test fun won() {
        assertEquals("980원", Format.won(980.0))
        assertEquals("1만 2,500원", Format.won(12_500.0))
        assertEquals("3만원", Format.won(30_000.0))
        assertEquals("123만 4,567원", Format.won(1_234_567.0))
        assertEquals("-4,000원", Format.won(-4_000.0))
        assertEquals("1센트 미만", Format.dollars(0.004))
    }

    @Test fun relative() {
        val now = Instant.ofEpochSecond(1_800_000_000)
        assertEquals("방금", Format.relative(now.minusSeconds(30), now))
        assertEquals("5분 전", Format.relative(now.minusSeconds(300), now))
        assertEquals("어제", Format.relative(now.minusSeconds(86_400), now))
        assertTrue(Format.deadline(now.plusSeconds(86_400), now).startsWith("내일 "))
        assertEquals("개발 봇이", Format.subject("개발 봇"))
        assertEquals("비서가", Format.subject("비서"))
    }
}

class RecordingTest {
    @Test fun chunkMath() {
        assertEquals(1, RecordingChunks.count(0, 10))
        assertEquals(3, RecordingChunks.count(25, 10))
        assertEquals(20L until 25L, RecordingChunks.range(2, 25, 10))
    }

    @Test fun uploadsInChunksResumesAndDeletesFile() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("rec").toFile()
        try {
            val queue = RecordingQueue(folder)
            val bytes = ByteArray(RecordingChunks.SIZE * 2 + 10) { 7 }
            java.io.File(folder, "a.m4a").writeBytes(bytes)
            queue.add(PendingRecording(id = "r1", fileName = "a.m4a", startedAt = 0, durationSec = 60.0, size = bytes.size.toLong()))

            val seen = mutableListOf<String>()
            val first = queue.upload { _, index, data ->
                if (index == 1) throw ApiError.Offline
                seen.add("$index:${data.size}")
                ChunkResult(received = index + 1, complete = false)
            }
            assertTrue(first.isEmpty())
            val reopened = RecordingQueue(folder)
            assertEquals("progress survives a restart", 1, reopened.pending().first().sentChunks)

            val done = reopened.upload { rec, index, data ->
                seen.add("$index:${data.size}")
                ChunkResult(received = index + 1, complete = index == rec.totalChunks - 1)
            }
            assertEquals(listOf("r1"), done)
            assertEquals(listOf("0:${RecordingChunks.SIZE}", "1:${RecordingChunks.SIZE}", "2:10"), seen)
            assertFalse(java.io.File(folder, "a.m4a").exists())
            assertTrue(reopened.pending().isEmpty())
        } finally { folder.deleteRecursively() }
    }
    private fun queueWith(folder: java.io.File, chunks: Int): RecordingQueue {
        val queue = RecordingQueue(folder)
        val bytes = ByteArray(chunks * 10) { 1 }
        java.io.File(folder, "a.m4a").writeBytes(bytes)
        runBlocking { queue.add(PendingRecording(id = "r1", fileName = "a.m4a", startedAt = 0, durationSec = 1.0, size = bytes.size.toLong())) }
        return queue
    }

    @Test fun goesBackToTheMacsCountAndDeletesOnlyWhenComplete() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("rec").toFile()
        try {
            // Real chunks are 4 MB; one small file of 3 chunks is enough with the real size since the queue reads by index.
            val queue = RecordingQueue(folder)
            val bytes = ByteArray(RecordingChunks.SIZE * 2 + 5) { 1 }
            java.io.File(folder, "a.m4a").writeBytes(bytes)
            queue.add(PendingRecording(id = "r1", fileName = "a.m4a", startedAt = 0, durationSec = 1.0, size = bytes.size.toLong()))
            val sent = mutableListOf<Int>()
            var lostOnce = true
            val done = queue.upload { _, index, _ ->
                sent.add(index)
                // The Mac lost chunk 1 the first time it came: it says it holds only chunk 0.
                if (index == 1 && lostOnce) { lostOnce = false; return@upload ChunkResult(received = 1, complete = false) }
                ChunkResult(received = index + 1, complete = index == 2)
            }
            assertEquals(listOf(0, 1, 1, 2), sent)
            assertEquals(listOf("r1"), done)

            // Every chunk sent but the Mac never says complete: the audio stays for later.
            val again = queueWith(folder, 1)
            val none = again.upload { _, _, _ -> ChunkResult(received = 1, complete = false) }
            assertTrue(none.isEmpty())
            assertTrue(java.io.File(folder, "a.m4a").exists())
            assertEquals(1, again.pending().size)
        } finally { folder.deleteRecursively() }
    }

    @Test fun refusedIsKeptWithItsReason() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("rec").toFile()
        try {
            val queue = queueWith(folder, 1)
            val told = mutableListOf<String>()
            queue.upload(refused = { told.add(it.id) }) { _, _, _ -> throw ApiError.BadRequest("shape") }
            assertEquals(listOf("r1"), told)
            val kept = queue.pending().single()
            assertEquals("보낸 내용을 맥이 이해하지 못했어요.", kept.problem)
            assertTrue(java.io.File(folder, "a.m4a").exists())
            // Skipped until 다시 보내기.
            assertTrue(queue.upload { _, _, _ -> error("not sent") }.isEmpty())
            queue.retry("r1")
            assertEquals(listOf("r1"), queue.upload { _, _, _ -> ChunkResult(1, true) })
            assertFalse(java.io.File(folder, "a.m4a").exists())
        } finally { folder.deleteRecursively() }
    }
}
