package dev.axkit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SSEMessage(val event: String, val id: String?, val data: String)

/** Server-sent events, line by line: `event`, `id`, `data`, `: comment`. */
class SSEParser {
    private var event = ""
    private var id: String? = null
    private val data = mutableListOf<String>()

    /** One line without its newline; returns a message when a blank line ends one. */
    fun feed(rawLine: String): SSEMessage? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) {
            val message = if (data.isEmpty()) null else SSEMessage(event.ifEmpty { "message" }, id, data.joinToString("\n"))
            event = ""; id = null; data.clear()
            return message
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon >= 0) line.substring(0, colon) else line
        var value = if (colon >= 0) line.substring(colon + 1) else ""
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "id" -> id = value
            "data" -> data.add(value)
        }
        return null
    }
}

/** Reconnect delays: 2 s doubling up to 60 s; back to 2 s after a connection that delivered something. */
class Backoff {
    var next = 2L; private set
    fun fail(): Long { val now = next; next = minOf(next * 2, 60); return now }
    fun reset() { next = 2 }
}

sealed interface StreamState {
    object Connecting : StreamState
    object Live : StreamState
    /** `attempt` counts failures in a row since the last good connection (1 = it just dropped). */
    data class Waiting(val retryInSec: Long, val attempt: Int = 1) : StreamState
    /** The token no longer works; pair again. */
    object Unauthorized : StreamState
}

/** One SSE connection to `GET stream` while the app is in front; resumes with `Last-Event-ID` after a drop. */
class EventStreamClient(private val client: ApiClient) {
    private var job: Job? = null
    /** The open request, so stop() closes the socket at once (§3.1 allows only 3 streams per device). */
    @Volatile private var call: okhttp3.Call? = null
    /** Wakes the wait between attempts (the network came back) or drops the open connection (start over from a new seq). */
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val http: OkHttpClient = client.http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build() // keepalive every 25 s

    fun start(
        scope: CoroutineScope,
        lastSeq: suspend () -> Int,
        onState: suspend (StreamState) -> Unit,
        onEvent: suspend (AppEvent) -> Unit,
        onActivity: suspend (Activity) -> Unit = {},
    ) {
        stop()
        job = scope.launch {
            val backoff = Backoff()
            var failures = 0
            while (isActive) {
                onState(StreamState.Connecting)
                wake.tryReceive() // a wake-up from before this attempt is used up
                try {
                    val c = http.newCall(client.streamRequest(lastSeq()))
                    call = c
                    val response = withContext(Dispatchers.IO) { c.await() }
                    try {
                        if (response.code == 401 && ApiClient.isOwnJSON(response.header("Content-Type"))) {
                            onState(StreamState.Unauthorized)
                            return@launch
                        }
                        if (response.code != 200) throw ApiError.Server(response.code, null)
                        // A good answer: the next drop starts again from a short wait, even if nothing arrived (a quiet room).
                        backoff.reset(); failures = 0
                        onState(StreamState.Live)
                        val source = response.body!!.source()
                        val parser = SSEParser()
                        while (isActive) {
                            val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                            val message = parser.feed(line) ?: continue
                            // `activity` is outside the log (no seq): it never moves the cursor.
                            Activity.parse(message.data)?.let { onActivity(it) }?.let { continue }
                            val event = EventDecoding.event(message.data) ?: continue
                            onEvent(event)
                        }
                    } finally {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { response.close() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                } catch (e: ApiError) {
                } finally {
                    call = null
                }
                if (!isActive) break
                failures++
                val wait = backoff.fail()
                onState(StreamState.Waiting(wait, failures))
                kotlinx.coroutines.withTimeoutOrNull(wait * 1000) { wake.receive() }
            }
        }
    }

    /** Try again now: the network is back, or the cursor moved (the server's log started over). */
    fun reconnect() {
        call?.cancel()
        wake.trySend(Unit)
    }

    fun stop() {
        job?.cancel(); job = null
        call?.cancel(); call = null
    }
}
