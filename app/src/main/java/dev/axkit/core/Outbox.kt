package dev.axkit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** A chat message waiting to reach the Mac. `id` is also the protocol `clientId`, so a retry is stored once. */
@Serializable
data class PendingMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val channel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    /** A quick answer (`choice` = an earlier message's `choices[].id`); `text` is then its label, for the bubble. */
    val choice: String? = null,
    /** From the system share sheet (`shared:true`). */
    val shared: Boolean = false,
    /** `waiting` → `sent` (the Mac took it; kept until its line reaches the timeline) · `failed` (refused, or too many tries). */
    val state: String = "waiting",
    /** The seq the Mac gave it, once sent. */
    val seq: Int? = null,
    /** Why it failed, in plain words. */
    val problem: String? = null,
    /** A quick answer: the message whose buttons it answers (that row unlocks again if this fails or is deleted). */
    val forSeq: Int? = null,
    /** A 결재 answer that could not reach the Mac at once: `POST cards/<cardId>` with this body (no chat bubble). */
    val cardId: String? = null,
    val cardBody: kotlinx.serialization.json.JsonObject? = null,
) {
    val isCardAnswer: Boolean get() = cardId != null
    val isFailed: Boolean get() = state == "failed"
    val isSent: Boolean get() = state == "sent"
}

data class FlushResult(
    val sent: List<String> = emptyList(),
    val dropped: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val remaining: Int = 0,
    /** The token stopped working: the queue is kept and sending pauses until pairing again. */
    val unauthorized: Boolean = false,
    /** Stopped on a passing problem; worth trying again after a wait. */
    val retryLater: Boolean = false,
)

/** Messages typed while offline, kept on disk (not secret; no tokens) and sent in order once the Mac is back. */
class Outbox(private val file: File?, private val now: () -> Long = System::currentTimeMillis) {
    /** Guards the list only (never held over the network), so a new bubble shows at once while a send hangs. */
    private val lock = Mutex()
    /** One flush at a time (a message is never sent twice in parallel). */
    private val flushLock = Mutex()
    private var queue: MutableList<PendingMessage> = file?.takeIf { it.exists() }?.let {
        runCatching { ProtocolJson.decodeFromString(ListSerializer(PendingMessage.serializer()), it.readText()).toMutableList() }.getOrNull()
    } ?: mutableListOf()

    companion object {
        /** One-time codes go stale; don't deliver them late (APP-PROTOCOL §6). */
        const val CODE_LIFETIME_MS = 10 * 60 * 1000L
        /** `POST messages` takes 8000 characters at most. */
        const val MAX_TEXT = 8000
        /** Busy answers (429/5xx) while the Mac is reachable: after this many the message waits for "다시 보내기". */
        const val MAX_BUSY_ATTEMPTS = 5

        /** "인증번호 482913", "code: 1234" … */
        fun looksLikeCode(text: String): Boolean {
            val lower = text.lowercase()
            val hasWord = listOf("인증", "otp", "code", "코드", "verification").any { lower.contains(it) }
            val hasDigits = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])").containsMatchIn(text)
            return hasWord && hasDigits
        }

        /** A long text in pieces of at most [max], cut at a line end or a space where there is one near the end. */
        fun split(text: String, max: Int = MAX_TEXT): List<String> {
            if (text.length <= max) return listOf(text)
            val parts = mutableListOf<String>()
            var rest = text
            while (rest.length > max) {
                val window = rest.substring(0, max)
                val cut = window.lastIndexOf('\n').takeIf { it > max / 2 } ?: window.lastIndexOf(' ').takeIf { it > max / 2 } ?: max
                parts.add(rest.substring(0, cut).trimEnd())
                rest = rest.substring(cut).trimStart()
            }
            if (rest.isNotEmpty()) parts.add(rest)
            return parts
        }

        /** Seconds before trying a busy Mac again: 2, 4, 8 … up to 30. */
        fun retryDelaySec(attempts: Int): Long = minOf(30L, 2L shl (attempts - 1).coerceIn(0, 4))
    }

    suspend fun pending(): List<PendingMessage> = lock.withLock { queue.toList() }

    suspend fun enqueue(text: String, channel: String, choice: String? = null, shared: Boolean = false, forSeq: Int? = null): PendingMessage =
        add(PendingMessage(text = text, channel = channel, createdAt = now(), choice = choice, shared = shared, forSeq = forSeq))

    /** A 결재 answer to send when the Mac is back. */
    suspend fun enqueueCard(cardId: String, body: kotlinx.serialization.json.JsonObject, channel: String): PendingMessage =
        add(PendingMessage(text = "", channel = channel, createdAt = now(), cardId = cardId, cardBody = body))

    private suspend fun add(message: PendingMessage): PendingMessage = lock.withLock { queue.add(message); persist(); message }

    /** Sent messages are dropped (the timeline started over, so their lines come again from the Mac). */
    suspend fun dropSent() = lock.withLock { queue.removeAll { it.isSent }; persist() }

    suspend fun remove(id: String) = lock.withLock { queue.removeAll { it.id == id }; persist() }
    suspend fun clear() = lock.withLock { queue.clear(); persist() }

    /** "다시 보내기": a failed (or stuck) message goes back in line with a fresh count. */
    suspend fun retry(id: String) = lock.withLock {
        val i = queue.indexOfFirst { it.id == id }
        if (i >= 0 && !queue[i].isSent) { queue[i] = queue[i].copy(state = "waiting", attempts = 0, problem = null); persist() }
    }

    /** Sent messages whose line the timeline now has (its seq is at or below [lastSeq]) leave the queue. */
    suspend fun confirm(lastSeq: Int): Boolean = lock.withLock {
        val before = queue.size
        queue.removeAll { it.isSent && (it.seq ?: 0) <= lastSeq }
        if (queue.size != before) { persist(); true } else false
    }

    /**
     * Sends the waiting ones, oldest first. A passing problem stops the run (order matters): offline waits for the Mac;
     * busy (429/5xx) counts a try and fails the message after [MAX_BUSY_ATTEMPTS]. A refusal fails that message only
     * (kept for 다시 보내기 · 복사 · 지우기). 401 keeps everything and stops.
     */
    suspend fun flush(send: suspend (PendingMessage) -> SendResult): FlushResult = flushLock.withLock {
        val sent = mutableListOf<String>(); val dropped = mutableListOf<String>(); val failed = mutableListOf<String>()
        var unauthorized = false; var retryLater = false
        val skip = HashSet<String>()
        while (true) {
            // Pick the next one under the lock, send without it, then write the outcome under it again.
            val message = lock.withLock {
                var next: PendingMessage? = null
                for (m in queue.toList()) {
                    if (m.state != "waiting" || m.id in skip) continue
                    if (looksLikeCode(m.text) && now() - m.createdAt > CODE_LIFETIME_MS) { queue.remove(m); dropped.add(m.id); continue }
                    next = m; break
                }
                next
            } ?: break
            var outcome: PendingMessage? = null
            var stop = false
            try {
                val result = send(message)
                outcome = message.copy(state = "sent", seq = result.seq.takeIf { it > 0 } ?: 0); sent.add(message.id)
            } catch (e: ApiError) {
                when {
                    e is ApiError.Unauthorized -> { unauthorized = true; stop = true }
                    e is ApiError.Offline -> { outcome = message.copy(attempts = message.attempts + 1); retryLater = true; stop = true }
                    e.isTransient -> {
                        val attempts = message.attempts + 1
                        if (attempts >= MAX_BUSY_ATTEMPTS) { outcome = message.copy(attempts = attempts, state = "failed", problem = "보내지 못했어요"); failed.add(message.id) }
                        else { outcome = message.copy(attempts = attempts); retryLater = true; stop = true }
                    }
                    else -> { outcome = message.copy(state = "failed", problem = e.friendlyMessage); failed.add(message.id) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = message.copy(attempts = message.attempts + 1); retryLater = true; stop = true
            }
            skip.add(message.id)
            lock.withLock {
                val i = queue.indexOfFirst { it.id == message.id }
                if (i >= 0 && outcome != null) queue[i] = outcome
                persist()
            }
            if (stop) break
        }
        FlushResult(sent, dropped, failed, lock.withLock { persist(); queue.count { it.state == "waiting" } }, unauthorized, retryLater)
    }

    private suspend fun persist() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistNow() }

    private fun persistNow() {
        val f = file ?: return
        if (queue.isEmpty()) { f.delete(); return }
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(ProtocolJson.encodeToString(ListSerializer(PendingMessage.serializer()), queue))
        tmp.renameTo(f)
    }
}
