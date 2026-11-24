package dev.axkit.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.axkit.core.PendingRecording
import java.io.File

/** Records speech to a small AAC file (about 11 MB per hour), same settings as iOS: AAC mono 16 kHz 24 kbps. */
class Recorder(private val context: Context) {
    var startedAt by mutableStateOf<Long?>(null); private set
    val isRecording: Boolean get() = startedAt != null
    var problem: String? = null

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedClock = 0L

    fun start(folder: File) {
        if (isRecording) return
        try {
            folder.mkdirs()
            val f = File(folder, "rec-${System.currentTimeMillis() / 1000}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f
            startedClock = SystemClock.elapsedRealtime()
            startedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            problem = "녹음을 시작하지 못했어요."
        }
    }

    /** Stops and returns what to send to the Mac. */
    fun stop(): PendingRecording? {
        val r = recorder ?: return null
        val f = file ?: return null
        val began = startedAt ?: return null
        val duration = (SystemClock.elapsedRealtime() - startedClock) / 1000.0
        runCatching { r.stop() }
        r.release()
        recorder = null; file = null; startedAt = null
        val size = f.length()
        if (duration < 1 || size <= 0) {
            f.delete()
            problem = "너무 짧아서 저장하지 않았어요."
            return null
        }
        return PendingRecording(fileName = f.name, startedAt = began, durationSec = duration, size = size)
    }
}
