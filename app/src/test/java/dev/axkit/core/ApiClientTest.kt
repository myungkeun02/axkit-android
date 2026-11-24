package dev.axkit.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private val base get() = server.url("/").toString().trimEnd('/')

    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() { server.shutdown() }

    private fun client(withAccess: Boolean = true) = ApiClient(
        ServerCredentials(base, "tok", "d_1", "안드로이드", if (withAccess) "cid" else null, if (withAccess) "csec" else null))

    private fun json(code: Int, body: String, type: String = "application/json") =
        MockResponse().setResponseCode(code).setHeader("Content-Type", type).setBody(body)

    private inline fun <reified T : Throwable> failsWith(block: () -> Unit): T {
        try { block() } catch (e: Throwable) { if (e is T) return e; throw e }
        fail("expected ${T::class.simpleName}"); throw IllegalStateException()
    }

    @Test fun sendsAuthHeadersAndChannel() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"seq":7}"""))
        val result = client().sendMessage(text = "hi", channel = "dev", clientId = "c1")
        assertEquals(7, result.seq)
        val r = server.takeRequest()
        assertEquals("Bearer tok", r.getHeader("Authorization"))
        assertEquals("cid", r.getHeader("CF-Access-Client-Id"))
        assertEquals("csec", r.getHeader("CF-Access-Client-Secret"))
        assertEquals("/m/v1/app/messages", r.path)
        val body = ProtocolJson.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("dev", body["channel"]?.jsonPrimitive?.content)
        assertEquals("c1", body["clientId"]?.jsonPrimitive?.content)
        assertNull(body["remember"])
    }

    @Test fun ownJson401IsUnauthorizedButProxyHtmlIsNot() = runBlocking {
        server.enqueue(json(401, """{"ok":false}"""))
        failsWith<ApiError.Unauthorized> { runBlocking { client().status() } }
        server.enqueue(json(401, "<html>", "text/html"))
        val e = failsWith<ApiError.Server> { runBlocking { client().status() } }
        assertEquals(401, e.status)
    }

    @Test fun viewUnwrapsDataAndGoneCard() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"view":"today","version":1,"data":{"date":"2026-11-19","events":null,"todos":[],"pendingCards":2}}"""))
        val today = client().view(TodayView.serializer(), "today")
        assertEquals(2, today.pendingCards)
        assertNull(today.events)
        assertEquals("/m/v1/app/views/today", server.takeRequest().path)
        server.enqueue(json(410, """{"ok":false,"error":"gone"}"""))
        failsWith<ApiError.Gone> { runBlocking { client().answerCard("org:d:1", CardAnswer.Text("내일")) } }
        val r = server.takeRequest()
        assertEquals("/m/v1/app/cards/org:d:1", r.path)
        assertEquals("내일", ProtocolJson.parseToJsonElement(r.body.readUtf8()).jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test fun rememberRidesAlongWithTheChoice() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"state":"approved"}"""))
        val result = client().answerCard("beans", CardAnswer.Option("beans:usual"), remember = true)
        assertEquals("approved", result.state)
        val body = ProtocolJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("beans:usual", body["option"]?.jsonPrimitive?.content)
        assertTrue(body["remember"]!!.jsonPrimitive.boolean)
    }

    @Test fun refusalShowsTheMacsOwnWords() {
        server.enqueue(json(400, """{"ok":false,"error":"key","message":"키가 맞지 않아요. 다시 확인해 주세요."}"""))
        val e = failsWith<Throwable> { runBlocking { client().connectProvider("openai", "sk-test-not-real") } }
        assertEquals("키가 맞지 않아요. 다시 확인해 주세요.", e.friendly)
    }

    @Test fun eventsDecodesEachIncludingUnknownTypeAndKeepsCursors() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"events":[{"type":"notice","seq":6,"at":"2026-11-18T06:19:46Z","text":"t"},{"type":"x","seq":7,"at":"z"}],"lastSeq":7,"oldestSeq":1}"""))
        val page = client().events(after = 5)
        assertTrue(server.takeRequest().path!!.contains("after=5"))
        assertEquals(listOf(6, 7), page.events.map { it.seq })
        assertTrue(page.events[1] is AppEvent.Unknown)
        assertEquals(7, page.lastSeq)
        assertEquals(1, page.oldestSeq)
    }

    @Test fun pairingSuccessIgnoresPublicHttpAddressFromServer() = runBlocking {
        val link = PairingLink.parse("axkit://pair?u=${java.net.URLEncoder.encode(base, "UTF-8")}&n=dev&c=123456")
        server.enqueue(json(200, """{"ok":true,"deviceId":"d_9","deviceName":"테오 폰","baseUrl":"http://example.com","token":"t"}"""))
        val credentials = ApiClient.pair(link)
        val r = server.takeRequest()
        assertNull(r.getHeader("Authorization"))
        assertEquals("/m/p/dev/app", r.path)
        assertEquals("""{"code":"123456"}""", r.body.readUtf8())
        assertEquals("d_9", credentials.deviceId)
        assertEquals("t", credentials.token)
        assertEquals("테오 폰", credentials.deviceName)
        assertEquals(base, credentials.baseUrl)
    }

    @Test fun pairingWrongCodeAndExpired() {
        val link = PairingLink.parse("axkit://pair?u=${java.net.URLEncoder.encode(base, "UTF-8")}&n=dev&c=123456")
        server.enqueue(json(403, """{"ok":false,"error":"wrong","attemptsLeft":3}"""))
        val e = failsWith<ApiError.WrongCode> { runBlocking { ApiClient.pair(link) } }
        assertEquals(3, e.attemptsLeft)
        server.enqueue(json(410, """{"ok":false,"error":"expired"}"""))
        failsWith<ApiError.PairingExpired> { runBlocking { ApiClient.pair(link) } }
    }

    @Test fun uploadChunkHeaders() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"received":3,"complete":true}"""))
        val rec = PendingRecording(id = "r1", fileName = "a.m4a", startedAt = 1_800_000_000_000L, durationSec = 59.6, size = (RecordingChunks.SIZE * 2 + 1).toLong())
        val result = client().uploadRecordingChunk(rec, 2, byteArrayOf(1, 2, 3))
        assertTrue(result.complete)
        assertEquals(3, result.received)
        val r = server.takeRequest()
        assertEquals("/m/v1/app/recordings/r1/chunks/2", r.path)
        assertEquals("3", r.getHeader("X-Chunk-Total"))
        assertEquals("2027-01-15T08:00:00Z", r.getHeader("X-Started-At"))
        assertEquals("60", r.getHeader("X-Duration-Sec"))
        assertEquals(3L, r.bodySize)
    }

    @Test fun viewActionPostsToActionPath() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"data":{"date":"d"}}"""))
        val v = client().viewAction(TodayView.serializer(), "today", "refresh", buildJsonObject { put("id", JsonPrimitive("x")) })
        assertEquals("d", v.date)
        assertEquals("/m/v1/app/views/today/refresh", server.takeRequest().path)
    }

    @Test fun offlineWhenNothingListens() {
        val c = client()
        server.shutdown()
        failsWith<ApiError.Offline> { runBlocking { c.status() } }
        server = MockWebServer().also { it.start() }
    }
}
