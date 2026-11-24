package dev.axkit.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.axkit.core.ApiClient
import dev.axkit.core.MacDictation
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * §30 받아쓰기 for one message box: record a short AAC clip with the phone's microphone, send the bytes to the Mac's
 * `POST dictation`, hand the words to the box. The clip lives in the app's cache only until it is sent (or dropped).
 */
class MacDictationRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: () -> ApiClient?,
    private val onText: (String) -> Unit,
) {
    enum class Phase { Idle, Recording, Transcribing }

    var phase by mutableStateOf(Phase.Idle); private set
    var elapsed by mutableFloatStateOf(0f); private set
    /** 0..1, the loudness right now (so the owner sees the microphone hears them). */
    var level by mutableFloatStateOf(0f); private set
    var problem by mutableStateOf<String?>(null)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var peak = 0
    private var ticker: Job? = null
    private var upload: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Tap: start → stop and send → (while the Mac writes) cancel. */
    fun toggle() = when (phase) {
        Phase.Idle -> start()
        Phase.Recording -> finish()
        Phase.Transcribing -> cancel()
    }

    /** Call after RECORD_AUDIO is granted. */
    fun start() {
        if (phase != Phase.Idle) return
        problem = null
        if (!hasPermission()) { problem = MacDictation.MIC_DENIED; return }
        val f = File(context.cacheDir, "dictation-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(f.absolutePath)
            r.setOnErrorListener { _, _, _ -> drop(); problem = MacDictation.STOPPED }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            problem = if (hasPermission()) MacDictation.STOPPED else MacDictation.MIC_DENIED
            return
        }
        recorder = r
        file = f
        peak = 0
        startedAt = System.currentTimeMillis()
        elapsed = 0f
        phase = Phase.Recording
        r.maxAmplitude // the first read is always 0; start the count here
        ticker = scope.launch {
            while (isActive && phase == Phase.Recording) {
                delay(80)
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                if (amp > peak) peak = amp
                level = MacDictation.meterOf(amp)
                elapsed = (System.currentTimeMillis() - startedAt) / 1000f
                if (elapsed >= MacDictation.MAX_SEC) { finish(); break }
            }
        }
    }

    /** Stops and sends to the Mac. */
    fun finish() {
        if (phase != Phase.Recording) return
        val r = recorder ?: return
        val f = file
        val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
        val loudest = peak
        recorder = null
        file = null
        ticker?.cancel()
        level = 0f
        // stop() throws when nothing was written yet (a very short tap): that is "too short" either way.
        val stopped = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!stopped || seconds < MacDictation.MIN_SEC || f == null || f.length() == 0L) {
            f?.delete(); phase = Phase.Idle; problem = MacDictation.TOO_SHORT; return
        }
        if (MacDictation.isSilent(loudest)) { f.delete(); phase = Phase.Idle; problem = MacDictation.MIC_SILENT; return }
        val api = client() ?: run { f.delete(); phase = Phase.Idle; problem = MacDictation.FAILED; return }
        phase = Phase.Transcribing
        upload = scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { f.readBytes() }
                withContext(Dispatchers.IO) { f.delete() }
                val answer = api.dictate(bytes, "audio/mp4")
                if (answer.text.isNotEmpty()) onText(answer.text) else problem = answer.message ?: MacDictation.NOTHING_HEARD
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = (e as? dev.axkit.core.ApiError.Refused)?.text ?: e.friendly
            } finally {
                f.delete()
                if (upload === coroutineContext[Job]) phase = Phase.Idle
            }
        }
    }

    /** A tap while the Mac is writing, or leaving the room: nothing is sent, nothing goes in the box. */
    fun cancel() {
        upload?.cancel()
        upload = null
        drop()
    }

    private fun drop() {
        ticker?.cancel()
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
        file?.delete()
        file = null
        level = 0f
        phase = Phase.Idle
    }
}
