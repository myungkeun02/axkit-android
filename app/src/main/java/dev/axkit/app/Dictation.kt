package dev.axkit.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Talk instead of typing: live Korean speech-to-text into the chat box. Nothing is recorded or kept;
 * recognition stays on the phone when the device can do it (port of Dictation.swift).
 */
class Dictation(private val context: Context) {
    var isListening by mutableStateOf(false); private set
    /** Words heard so far in this session (replaces itself as recognition improves). */
    var heard by mutableStateOf(""); private set
    var problem by mutableStateOf<String?>(null)

    private var recognizer: SpeechRecognizer? = null
    private var triedOnDevice = false

    companion object {
        const val PERMISSION_PROBLEM = "말로 입력하려면 설정 › ax-kit에서 마이크와 음성 인식을 켜 주세요."
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Call after RECORD_AUDIO is granted. */
    fun start() {
        if (isListening) return
        if (!hasPermission()) { problem = PERMISSION_PROBLEM; return }
        val onDevice = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            problem = "지금은 음성 인식을 쓸 수 없어요."
            return
        }
        heard = ""
        begin(onDevice)
    }

    private fun begin(onDevice: Boolean) {
        try {
            triedOnDevice = onDevice
            val r = if (onDevice && Build.VERSION.SDK_INT >= 33) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            isListening = true
            r.startListening(intent)
        } catch (e: Exception) {
            problem = "말로 입력하기를 시작하지 못했어요."
            stop()
        }
    }

    fun stop() {
        if (!isListening && recognizer == null) return
        recognizer?.let { runCatching { it.stopListening() }; runCatching { it.destroy() } }
        recognizer = null
        isListening = false
    }

    private fun words(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) { words(partialResults)?.let { heard = it } }
        override fun onResults(results: Bundle?) {
            words(results)?.let { heard = it }
            stop()
        }
        override fun onError(error: Int) {
            // The phone's own recogniser may not have Korean: try the regular one (still preferring offline).
            val noKorean = error == 12 /* ERROR_LANGUAGE_NOT_SUPPORTED */ || error == 13 /* ERROR_LANGUAGE_UNAVAILABLE */
            if (triedOnDevice && (noKorean || error == SpeechRecognizer.ERROR_CLIENT) && heard.isEmpty()) {
                recognizer?.let { runCatching { it.destroy() } }
                recognizer = null
                isListening = false
                if (SpeechRecognizer.isRecognitionAvailable(context)) { begin(onDevice = false); return }
            }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) problem = PERMISSION_PROBLEM
            stop()
        }
    }
}
