package dev.axkit.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * §30 받아쓰기: the owner speaks, the Mac turns it into words, and the words go into the message box where the owner
 * fixes them and sends. Nothing is sent on its own. Pure parts, so the words and the limits are unit-tested.
 */
object MacDictation {
    /** Recording stops by itself after 3 minutes (longer talks belong in 회의 녹음). */
    const val MAX_SEC = 180
    /** Under this a recording is just a tap. */
    const val MIN_SEC = 1.0
    /**
     * `MediaRecorder.getMaxAmplitude()` is 0..32767; below this for the whole recording (about -55 dBFS) the microphone
     * heard nothing, so it is not sent.
     */
    const val SILENT_AMPLITUDE = 60

    const val TOO_SHORT = "너무 짧아요. 말한 뒤에 다시 눌러 주세요."
    const val MIC_DENIED = "마이크를 쓸 수 없어요. 설정 › 앱 › ax-kit › 권한에서 마이크를 허용해 주세요."
    const val MIC_SILENT = "마이크 소리가 들어오지 않아요. 다른 앱이 마이크를 쓰고 있지 않은지 보고, 휴대폰 마이크에 가까이 말해 주세요."
    const val NOTHING_HEARD = "말소리를 찾지 못했어요. 조금 더 크게 말해 주세요."
    const val FAILED = "받아쓰지 못했어요. 다시 해 주세요."
    const val STOPPED = "녹음이 멈췄어요. 마이크를 확인하고 다시 눌러 주세요."

    /** The Mac's answer: `{text}`, with `message` when it heard nothing (or on a refusal). */
    data class Answer(val text: String, val message: String?)

    fun decode(body: JsonObject?): Answer {
        fun str(key: String) = (body?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
        return Answer(str("text") ?: "", str("message")?.takeIf { it.isNotEmpty() }?.take(300))
    }

    /** 0..32767 amplitude → 0..1 for the little meter (-60 dBFS and below is empty). */
    fun meterOf(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val db = 20 * kotlin.math.log10(amplitude / 32767.0)
        return ((db + 60) / 60).coerceIn(0.0, 1.0).toFloat()
    }

    fun isSilent(peakAmplitude: Int) = peakAmplitude < SILENT_AMPLITUDE

    /** Result of putting words in the box: the new text and where the caret goes (right after the words). */
    data class Inserted(val text: String, val caret: Int)

    /** Puts `words` where the caret is (replacing a selection), with a space between it and a neighbouring word. */
    fun insert(value: String, start: Int, end: Int, words: String): Inserted {
        val w = words.trim()
        val s = start.coerceIn(0, value.length)
        val e = end.coerceIn(s, value.length)
        if (w.isEmpty()) return Inserted(value, e)
        val before = value.substring(0, s)
        val after = value.substring(e)
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trail = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        return Inserted(before + lead + w + trail + after, before.length + lead.length + w.length)
    }

    /** 0:07, 1:42 */
    fun clock(sec: Double): String {
        val s = maxOf(0, sec.toInt())
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}
