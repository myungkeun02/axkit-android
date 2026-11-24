package dev.axkit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

/** §22 `views/browser-live`: a page the browser errand stopped on, which the owner finishes from the phone. */
@Serializable
data class BrowserLiveState(
    val live: Boolean = false,
    val url: String? = null,
    val title: String? = null,
    /** `login` · `payment` · `consent` · `captcha` · `other`. */
    val reason: String? = null,
    val startedAt: IsoInstant? = null,
    val idleClosesAt: IsoInstant? = null,
    val pending: Pending? = null,
    /** Why the last session ended: `done` · `idle` · `stopped` · `error` · `shutdown`. */
    val closed: String? = null,
    val frameUrl: String? = null,
    val streamUrl: String? = null,
    val resumed: Boolean? = null,
    /** `done`: where the errand carries on — `app` (this app's chat or a room) or `elsewhere` (Discord). */
    val continuedIn: String? = null,
    /** `done`: the room the errand was asked in, when it was a room. */
    val channel: String? = null,
) {
    @Serializable data class Pending(val url: String? = null, val reason: String? = null, val at: IsoInstant? = null)
}

/** One picture of the page. Coordinates sent back are always in these JPEG pixels. */
class BrowserFrame(val seq: Int, val url: String?, val title: String?, val width: Int, val height: Int, val jpeg: ByteArray)

@Serializable private data class FrameJson(val seq: Int = 0, val url: String? = null, val title: String? = null, val width: Int = 0, val height: Int = 0, val jpeg: String = "")

object BrowserLiveWords {
    /** Plain words for why the page was handed over. */
    fun reason(r: String?): String = when (r) {
        "login" -> "로그인이 필요해요"
        "payment" -> "마지막 결제 버튼은 직접 눌러 주세요"
        "consent" -> "약관 동의가 필요해요"
        "captcha" -> "사람인지 확인하는 단계예요"
        else -> "직접 해 주셔야 하는 단계예요"
    }

    /** Why the page closed (null when it is still open or the owner finished it). */
    fun closed(c: String?): String? = when (c) {
        "idle" -> "한동안 아무것도 하지 않아서 브라우저 화면을 닫았어요."
        "stopped" -> "비상 정지로 브라우저 화면을 닫았어요."
        "shutdown" -> "맥의 비서가 다시 시작해서 브라우저 화면이 닫혔어요."
        "error" -> "브라우저 화면에 문제가 생겨 닫혔어요."
        else -> null
    }

    /**
     * A chat line that hands a browser page to the owner (§22: "앱의 브라우저 화면에서 직접 하실 수 있어요…" or
     * browser.open_for_me's "앱의 브라우저 화면에 준비해 뒀어요…"), but not the owner's own "직접 마쳤어요" after done.
     */
    /**
     * Whether a chat line gets the "브라우저 화면 열기" button: the `live` field when the server sends it (§22);
     * the sentence check only for older servers without it.
     */
    fun opensLive(message: MessageEvent): Boolean {
        if (message.isOwner) return false
        val live = message.live ?: return handoff(message.text)
        return live.view == "browser-live"
    }

    /** After `done`: the room to go back to (null = stay put, the errand carries on outside the app). */
    fun returnRoom(s: BrowserLiveState): String? = if (s.continuedIn == "elsewhere") null else s.channel ?: ""

    /** After `done`, in plain words. */
    fun doneWords(s: BrowserLiveState): String = when {
        s.resumed != true -> "브라우저 화면을 닫았어요."
        s.continuedIn == "elsewhere" -> "브라우저 화면에서 마친 일은 디스코드 대화에서 이어 갈게요."
        else -> "비서에게 이어서 하라고 했어요."
    }

    fun handoff(text: String): Boolean = text.contains("앱의 브라우저 화면") && !text.contains("직접 마쳤어요")

    /** Picture coordinates → JPEG pixels for a picture shown aspect-fit in `shownW`×`shownH`. Null outside the picture. */
    fun pixel(x: Float, y: Float, shownW: Float, shownH: Float, imageW: Int, imageH: Int): Pair<Int, Int>? {
        if (imageW <= 0 || imageH <= 0 || shownW <= 0 || shownH <= 0) return null
        val scale = minOf(shownW / imageW, shownH / imageH)
        val ox = (shownW - imageW * scale) / 2; val oy = (shownH - imageH * scale) / 2
        val px = (x - ox) / scale; val py = (y - oy) / scale
        if (px < 0 || py < 0 || px > imageW || py > imageH) return null
        return Math.round(px).coerceIn(0, imageW) to Math.round(py).coerceIn(0, imageH)
    }
}

/**
 * `GET browser-live/stream` (SSE `state` / `frame`). If the stream can't be opened, falls back to
 * `GET browser-live/frame` every 2 s (that one counts against the 60-a-minute limit).
 */
class BrowserLiveStream(private val client: ApiClient) {
    private var job: Job? = null
    private val http = client.http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun start(scope: CoroutineScope, onState: suspend (BrowserLiveState) -> Unit, onFrame: suspend (BrowserFrame) -> Unit) {
        job?.cancel()
        job = scope.launch {
            var streamFailures = 0
            while (isActive) {
                var gotSomething = false
                try {
                    val request = client.requestBuilder("browser-live/stream").header("Accept", "text/event-stream").get().build()
                    val response = withContext(Dispatchers.IO) { http.newCall(request).await() }
                    try {
                        if (response.code != 200) throw ApiError.Server(response.code, null)
                        val source = response.body!!.source()
                        val parser = SSEParser()
                        while (isActive) {
                            val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                            val m = parser.feed(line) ?: continue
                            gotSomething = true
                            when (m.event) {
                                "state" -> runCatching { ProtocolJson.decodeFromString(BrowserLiveState.serializer(), m.data) }.getOrNull()?.let { onState(it) }
                                "frame" -> decodeFrame(m.data)?.let { onFrame(it) }
                            }
                        }
                    } finally { withContext(Dispatchers.IO) { response.close() } }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                if (!gotSomething) streamFailures++ else streamFailures = 0
                if (streamFailures >= 2) { poll(onFrame); return@launch }
                delay(2000)
            }
        }
    }

    private suspend fun poll(onFrame: suspend (BrowserFrame) -> Unit) {
        var seq = 0
        while (true) {
            try {
                val bytes = client.file("browser-live/frame")
                val size = jpegSize(bytes)
                onFrame(BrowserFrame(++seq, null, null, size?.first ?: 0, size?.second ?: 0, bytes))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            delay(2000)
        }
    }

    fun stop() { job?.cancel(); job = null }

    companion object {
        fun decodeFrame(data: String): BrowserFrame? = runCatching {
            val f = ProtocolJson.decodeFromString(FrameJson.serializer(), data)
            val bytes = Base64.getDecoder().decode(f.jpeg)
            val size = if (f.width > 0 && f.height > 0) f.width to f.height else jpegSize(bytes) ?: (0 to 0)
            BrowserFrame(f.seq, f.url, f.title, size.first, size.second, bytes)
        }.getOrNull()

        /** Width and height from a JPEG's SOF marker (no Android needed, so it is testable). */
        fun jpegSize(b: ByteArray): Pair<Int, Int>? {
            var i = 2
            while (i + 9 < b.size) {
                if (b[i].toInt() and 0xff != 0xFF) { i++; continue }
                val marker = b[i + 1].toInt() and 0xff
                val len = ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    val h = ((b[i + 5].toInt() and 0xff) shl 8) or (b[i + 6].toInt() and 0xff)
                    val w = ((b[i + 7].toInt() and 0xff) shl 8) or (b[i + 8].toInt() and 0xff)
                    return w to h
                }
                i += 2 + len
            }
            return null
        }
    }
}
