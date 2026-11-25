package dev.axkit.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File
import java.nio.file.Files

class SignalsTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(20)

    private val now = 1_790_000_000_000L

    private fun sms(text: String, at: Long = now) = SignalItem(kind = "sms", at = at, from = "1588-0000", text = text)

    @Test fun bodyHasTsAndOnlyKnownFields() {
        val body = Signals.body(listOf(
            SignalItem(kind = "notification", at = now, app = "토스", title = "결제", text = "스타벅스 5,600원 결제", lat = 1.0),
            SignalItem(kind = "location", at = now - 60_000, lat = 37.5, lng = 127.03, label = "서울특별시 강남구", accuracy = 30.0),
            SignalItem(kind = "battery", at = now, level = 18),
        ), now)
        assertEquals(1, body["v"]!!.jsonPrimitive.content.toInt())
        assertEquals("2026-09-21T14:13:20Z", body["ts"]!!.jsonPrimitive.content)
        val items = body["items"]!!.jsonArray
        val n = items[0].jsonObject
        assertEquals(setOf("kind", "at", "app", "title", "text"), n.keys)
        val l = items[1].jsonObject
        assertEquals(setOf("kind", "at", "lat", "lng", "label", "accuracy"), l.keys)
        assertEquals("2026-09-21T14:12:20Z", l["at"]!!.jsonPrimitive.content)
        assertEquals(setOf("kind", "at", "level"), items[2].jsonObject.keys)
    }

    @Test fun longTextIsCutToWhatTheMacTakes() {
        val n = SignalItem(kind = "notification", at = now, app = "카카오톡", text = "가".repeat(500)).wire()
        assertEquals(200, n["text"]!!.jsonPrimitive.content.length)
        val s = sms("나".repeat(1500)).wire()
        assertEquals(1000, s["text"]!!.jsonPrimitive.content.length)
    }

    @Test fun unsendableItemsAreNeverQueued() = runTest {
        val q = SignalQueue(null) { now }
        q.add(listOf(
            sms("   "),
            SignalItem(kind = "notification", at = now, app = "", text = "x"),
            SignalItem(kind = "location", at = now, lat = 0.0, lng = 0.0),
            SignalItem(kind = "battery", at = now, level = 140),
            SignalItem(kind = "steps", at = now, steps = 5000, date = "어제"),
            SignalItem(kind = "screen", at = now),
            sms("인증번호 123456"),
        ))
        assertEquals(1, q.count())
    }

    @Test fun flushSendsInBatchesOfFifty() = runTest {
        val q = SignalQueue(null) { now }
        q.add((1..120).map { sms("문자 $it") })
        val sizes = mutableListOf<Int>()
        val result = q.flush { body -> sizes += (body["items"] as JsonArray).size; """{"ok":true,"stored":${(body["items"] as JsonArray).size}}""" }
        assertEquals(listOf(50, 50, 20), sizes)
        assertEquals(120, result.sent)
        assertEquals(0, result.remaining)
        assertEquals(setOf("sms"), result.sentKinds)
    }

    @Test fun offlineKeepsEverythingForLater() = runTest {
        val q = SignalQueue(null) { now }
        q.add(listOf(sms("a"), sms("b")))
        val result = q.flush { throw ApiError.Offline }
        assertTrue(result.retryLater)
        assertFalse(result.unauthorized)
        assertEquals(2, result.remaining)
    }

    @Test fun unauthorizedStopsAndKeeps() = runTest {
        val q = SignalQueue(null) { now }
        q.add(sms("a"))
        val result = q.flush { throw ApiError.Unauthorized }
        assertTrue(result.unauthorized)
        assertFalse(result.retryLater)
        assertEquals(1, result.remaining)
    }

    @Test fun oneRefusedItemDoesNotBlockTheRest() = runTest {
        val q = SignalQueue(null) { now }
        q.add(listOf(sms("좋은 1"), sms("나쁜"), sms("좋은 2")))
        val result = q.flush { body ->
            val texts = (body["items"] as JsonArray).map { it.jsonObject["text"]!!.jsonPrimitive.content }
            if ("나쁜" in texts) throw ApiError.BadRequest("shape") else """{"ok":true,"stored":${texts.size}}"""
        }
        assertEquals(2, result.sent)
        assertEquals(1, result.dropped)
        assertEquals(0, result.remaining)
    }

    @Test fun anEmptyTextTheMacExplainsIsDropped() = runTest {
        val q = SignalQueue(null) { now }
        q.add(sms("a"))
        val result = q.flush { """{"ok":false,"problem":"문자 내용이 비어 있어요."}""" }
        assertEquals(1, result.dropped)
        assertEquals(0, result.remaining)
    }

    @Test fun itemsTooOldForTheMacAreDropped() = runTest {
        var clock = now
        val q = SignalQueue(null) { clock }
        q.add(listOf(sms("old", at = now - 46 * 3_600_000L), sms("new")))
        clock = now + 2 * 3_600_000L
        val sent = mutableListOf<String>()
        q.flush { body -> (body["items"] as JsonArray).forEach { sent += it.jsonObject["text"]!!.jsonPrimitive.content }; """{"ok":true,"stored":1}""" }
        assertEquals(listOf("new"), sent)
    }

    @Test fun queueSurvivesARestartAndIsGoneOnceSent() = runTest {
        val dir = Files.createTempDirectory("signals").toFile()
        val file = File(dir, "q.json")
        SignalQueue(file) { now }.add(listOf(sms("a"), SignalItem(kind = "battery", at = now, level = 15)))
        val again = SignalQueue(file) { now }
        assertEquals(2, again.count())
        again.flush { """{"ok":true,"stored":2}""" }
        assertEquals(0, SignalQueue(file) { now }.count())
        dir.deleteRecursively()
    }

    @Test fun ingestPostsToTheMacWithTheAppToken() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"ok":true,"stored":1}"""))
        server.start()
        val base = server.url("").toString().trimEnd('/')
        val client = ApiClient(ServerCredentials(base, "tok-1", "d1", "폰"))
        val q = SignalQueue(null)
        q.add(sms("[Web발신] 신한카드 승인 12,000원", at = System.currentTimeMillis()))
        val result = q.flush { client.ingest(it) }
        val req = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/m/v1/ingest", req.path)
        assertEquals("Bearer tok-1", req.getHeader("Authorization"))
        val body = ProtocolJson.parseToJsonElement(req.body.readUtf8()) as JsonObject
        assertEquals("sms", body["items"]!!.jsonArray[0].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(1, result.sent)
        server.shutdown()
    }

    @Test fun serverErrorMeansRetry() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.start()
        val client = ApiClient(ServerCredentials(server.url("").toString().trimEnd('/'), "t", "d", "폰"))
        val q = SignalQueue(null)
        q.add(sms("a", at = System.currentTimeMillis()))
        val result = q.flush { client.ingest(it) }
        assertTrue(result.retryLater)
        assertEquals(1, result.remaining)
        server.shutdown()
    }

    @Test fun storedReadsTheAnswer() {
        assertEquals(3, Signals.stored("""{"ok":true,"stored":3}"""))
        assertNull(Signals.stored("""{"ok":false,"problem":"x"}"""))
        assertNull(Signals.stored("<html>"))
    }

    @Test fun batteryNoticeOnceBelowTwentyUntilCharged() {
        var armed = true
        fun read(level: Int, charging: Boolean = false): Boolean { val (send, a) = BatteryRule.check(level, charging, armed); armed = a; return send }
        assertFalse(read(40))
        assertTrue(read(19))
        assertFalse(read(15))
        assertFalse(read(25))
        assertFalse(read(10))
        assertFalse(read(12, charging = true))
        assertTrue(read(18))
        assertFalse(read(31))
        assertTrue(read(19))
    }

    @Test fun sameNoticeOnlyOnceInTenMinutes() {
        val seen = SeenNotices()
        assertTrue(seen.firstTime("k", now))
        assertFalse(seen.firstTime("k", now + 60_000))
        assertTrue(seen.firstTime("other", now))
        assertTrue(seen.firstTime("k", now + 11 * 60_000))
    }

    @Test fun defaultAppsAreMessagesKakaoAndMoneyApps() {
        assertTrue(Signals.isDefaultApp("com.kakao.talk", "카카오톡"))
        assertTrue(Signals.isDefaultApp("viva.republica.toss", "토스"))
        assertTrue(Signals.isDefaultApp("com.google.android.apps.messaging", "메시지"))
        assertTrue(Signals.isDefaultApp("com.example.sms", "문자", smsApp = "com.example.sms"))
        assertTrue(Signals.isDefaultApp("kr.some.unknown", "OO카드"))
        assertTrue(Signals.isDefaultApp("kr.some.other", "우리은행"))
        assertFalse(Signals.isDefaultApp("com.instagram.android", "Instagram"))
        assertFalse(Signals.isDefaultApp("com.google.android.youtube", "YouTube"))
    }

    @Test fun noticeTextPrefersTheLastChatLineAndSkipsCounts() {
        assertEquals("내일 3시 괜찮아요?", Signals.noticeText("새 메시지 2개", null, "내일 3시 괜찮아요?"))
        assertEquals("길게 펼친 내용", Signals.noticeText("짧은", "길게  펼친\n내용", null))
        assertNull(Signals.noticeText("새 메시지 3개", null, null))
        assertNull(Signals.noticeText("  ", null, null))
    }
}
