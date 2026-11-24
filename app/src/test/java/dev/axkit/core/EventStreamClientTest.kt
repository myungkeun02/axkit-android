package dev.axkit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventStreamClientTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() { scope.cancel(); server.shutdown() }

    private fun api() = ApiClient(ServerCredentials(server.url("/").toString().trimEnd('/'), "tok", "d", "n"))

    private fun sse(seq: Int) = "id: $seq\nevent: message\ndata: {\"type\":\"notice\",\"seq\":$seq,\"at\":\"2026-11-18T06:19:46Z\",\"text\":\"t$seq\"}\n\n"

    @Test fun sendsLastEventIdAndResumesAfterDrop() = runBlocking {
        // First connection: one event then the server closes. Second: another event, then stays open.
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse(5)))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse(6)).setSocketPolicy(SocketPolicy.KEEP_OPEN))

        var last = 4
        val got = Channel<Int>(Channel.UNLIMITED)
        val client = EventStreamClient(api())
        client.start(scope, lastSeq = { last }, onState = {}, onEvent = { last = it.seq; got.send(it.seq) })

        assertEquals(5, withTimeout(5_000) { got.receive() })
        // Backoff after a delivering connection is 2 s; the reconnect must carry the new cursor.
        assertEquals(6, withTimeout(8_000) { got.receive() })
        client.stop()

        val first = server.takeRequest()
        assertEquals("4", first.getHeader("Last-Event-ID"))
        assertEquals("text/event-stream", first.getHeader("Accept"))
        assertEquals("Bearer tok", first.getHeader("Authorization"))
        assertEquals("/m/v1/app/stream", first.path)
        assertEquals("5", server.takeRequest().getHeader("Last-Event-ID"))
    }

    @Test fun ownJson401StopsAsUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody("""{"ok":false}"""))
        val states = Channel<StreamState>(Channel.UNLIMITED)
        EventStreamClient(api()).start(scope, lastSeq = { 0 }, onState = { states.send(it) }, onEvent = {})
        assertEquals(StreamState.Connecting, withTimeout(5_000) { states.receive() })
        assertEquals(StreamState.Unauthorized, withTimeout(5_000) { states.receive() })
    }
}
