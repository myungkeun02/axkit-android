package dev.axkit.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** A recording on this phone that has not fully reached the Mac yet (R14). */
@Serializable
data class PendingRecording(
    val id: String = "r_" + UUID.randomUUID().toString().replace("-", "").take(12).lowercase(),
    /** File name inside the recordings folder (never a full path). */
    val fileName: String,
    val startedAt: Long,
    val durationSec: Double,
    val size: Long,
    /** Chunks the Mac confirmed, in order; upload resumes from here. */
    val sentChunks: Int = 0,
    /** The Mac refused it for good (plain words); kept with its audio until the owner sends it again or deletes it. */
    val problem: String? = null,
) {
    val totalChunks: Int get() = RecordingChunks.count(size)
    val progress: Double get() = if (totalChunks == 0) 1.0 else sentChunks.toDouble() / totalChunks
}

object RecordingChunks {
    /** 4 MB stays well under the Mac's 12 MB upload limit and is quick to retry on a bad connection. */
    const val SIZE = 4 * 1024 * 1024

    fun count(size: Long, chunk: Int = SIZE): Int = maxOf(1, ((size + chunk - 1) / chunk).toInt())

    fun range(index: Int, size: Long, chunk: Int = SIZE): LongRange {
        val start = index.toLong() * chunk
        return start until minOf(size, start + chunk)
    }

    fun read(file: File, index: Int, chunk: Int = SIZE): ByteArray {
        val r = range(index, file.length(), chunk)
        val bytes = ByteArray((r.last - r.first + 1).coerceAtLeast(0).toInt())
        RandomAccessFile(file, "r").use { it.seek(r.first); it.readFully(bytes) }
        return bytes
    }
}

/** Recordings waiting to reach the Mac. The list is kept on disk; the audio files sit next to it until the Mac has every chunk. */
class RecordingQueue(val folder: File) {
    private val lock = Mutex()
    private val uploadLock = Mutex()
    private val listFile get() = File(folder, "queue.json")
    private var items: MutableList<PendingRecording>

    init {
        folder.mkdirs()
        items = runCatching {
            ProtocolJson.decodeFromString(ListSerializer(PendingRecording.serializer()), listFile.readText()).toMutableList()
        }.getOrDefault(mutableListOf())
    }

    suspend fun pending(): List<PendingRecording> = lock.withLock { items.toList() }

    fun file(recording: PendingRecording) = File(folder, recording.fileName)

    suspend fun add(recording: PendingRecording) = lock.withLock { items.add(recording); persist() }

    /**
     * Sends what is left, oldest first. Resumes from the Mac's own count (`received` = chunks it holds from 0 with no gap),
     * so a chunk it lost is sent again. The audio is deleted only when the Mac says `complete`. A refusal keeps the
     * recording with its reason ([refused] tells the screen); a network problem stops and keeps its place.
     */
    suspend fun upload(
        onProgress: suspend (List<PendingRecording>) -> Unit = {},
        refused: suspend (PendingRecording) -> Unit = {},
        send: suspend (PendingRecording, Int, ByteArray) -> ChunkResult,
    ): List<String> = uploadLock.withLock {
        val finished = mutableListOf<String>()
        val done = HashSet<String>()
        // The list lock is taken only to read or write the list, never over the network: adding a new recording never waits.
        suspend fun save(r: PendingRecording?, removeId: String? = null) = lock.withLock {
            if (r != null) { val k = items.indexOfFirst { it.id == r.id }; if (k >= 0) items[k] = r }
            if (removeId != null) items.removeAll { it.id == removeId }
            persist()
            items.toList()
        }
        outer@ while (true) {
            val recording = lock.withLock { items.firstOrNull { it.problem == null && it.id !in done } } ?: break
            done.add(recording.id)
            val f = file(recording)
            if (!withContext(Dispatchers.IO) { f.exists() }) { save(null, recording.id); continue } // cleared storage
            var current = recording.copy(size = withContext(Dispatchers.IO) { f.length() })
            var stuck = 0
            var rounds = 0
            try {
                var complete = false
                while (!complete) {
                    if (current.sentChunks >= current.totalChunks) {
                        // Every chunk went out but the Mac is not done: send the last one again, a few times at most.
                        if (++rounds > 2) break@outer
                        current = current.copy(sentChunks = current.totalChunks - 1)
                    }
                    val index = current.sentChunks
                    val bytes = withContext(Dispatchers.IO) { RecordingChunks.read(f, index) }
                    val result = send(current, index, bytes)
                    complete = result.complete
                    val next = result.received.coerceIn(0, current.totalChunks)
                    if (!complete && next <= index) { if (++stuck > 2) break@outer } else stuck = 0
                    current = current.copy(sentChunks = next)
                    onProgress(save(current))
                }
                withContext(Dispatchers.IO) { f.delete() }
                save(null, current.id)
                finished.add(current.id)
            } catch (e: ApiError) {
                if (e.isTransient || e is ApiError.Unauthorized) break
                val failed = current.copy(problem = e.friendlyMessage)
                save(failed)
                refused(failed)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
        }
        onProgress(pending())
        finished
    }

    /** "다시 보내기" after a refusal. */
    suspend fun retry(id: String) = lock.withLock {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(problem = null); persist() }
    }

    /** Deletes a recording and its audio (only when the owner asks). */
    suspend fun remove(id: String) = lock.withLock {
        items.firstOrNull { it.id == id }?.let { withContext(Dispatchers.IO) { file(it).delete() } }
        items.removeAll { it.id == id }; persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) { persistNow() }
    private fun persistNow() { listFile.writeText(ProtocolJson.encodeToString(ListSerializer(PendingRecording.serializer()), items)) }
}
