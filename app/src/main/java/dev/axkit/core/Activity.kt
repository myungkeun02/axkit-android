package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * §4 `activity` (feature `activity`, outside the log): the assistant is thinking or working on the owner's words.
 * No `seq` and never replayed; `Last-Event-ID` stays the log's seq. `channel` missing = the main chat.
 */
@Serializable
data class Activity(
    val channel: String? = null,
    /** `thinking` · `working` · `idle`. */
    val state: String = "idle",
    /** Plain words while working ("메일 보는 중"). */
    val step: String? = null,
    val at: IsoInstant? = null,
) {
    val room: String get() = channel ?: ""
    val isIdle: Boolean get() = state == "idle"

    /** What the chat shows under the last line. */
    val words: String get() = if (state == "working" && !step.isNullOrBlank()) step.trimEnd('…', '.', ' ') + "…" else "생각하는 중…"

    companion object {
        /** How long a busy room stays marked without news (the server forgets it after 3 minutes too). */
        const val STALE_MS = 3 * 60 * 1000L

        fun parse(text: String): Activity? = runCatching {
            val obj = ProtocolJson.parseToJsonElement(text) as? JsonObject ?: return null
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "activity") return null
            ProtocolJson.decodeFromJsonElement<Activity>(obj)
        }.getOrNull()
    }
}
