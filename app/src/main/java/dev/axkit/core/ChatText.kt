package dev.axkit.core

import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Cleans what the Mac sends before it is shown, so leftovers from Discord, logs and prompts never reach the screen
 * even when the server has not caught up: mentions, emoji codes, internal ids, `key: value` metadata, prompt blocks.
 * Port of AxKitCore/ChatText.swift (same rules, same tests).
 */
object ChatText {
    private val cleanCache = ConcurrentHashMap<String, String>()
    private val previewCache = ConcurrentHashMap<String, String>()

    /** Text for a chat bubble or a list line (keeps inline markdown, turns block markdown into plain lines). */
    fun clean(raw: String, zone: ZoneId = Format.seoul): String {
        if (raw.isEmpty()) return ""
        val key = zone.id + "\u0001" + raw
        cleanCache[key]?.let { return it }
        val text = cleanUncached(raw, zone)
        if (cleanCache.size > 6000) cleanCache.clear()
        cleanCache[key] = text
        return text
    }

    internal fun cleanUncached(raw: String, zone: ZoneId): String {
        var text = raw.replace("\r\n", "\n")
        text = dropPromptBlocks(text)
        text = cutMetadata(text)
        text = discord(text, zone)
        text = plainLines(text)
        text = dropInternalIds(text)
        text = glossary(text)
        // Addresses read as written ("…/__없는-주소"), not as %EC%97….
        text = Regex("""https?://[^\s\])]+""").replace(text) { m ->
            if (m.value.contains("%")) runCatching { URLDecoder.decode(m.value.replace("+", "%2B"), "UTF-8") }.getOrDefault(m.value) else m.value
        }
        text = text.replace(Regex("""(?<=\S)[ \t]{2,}"""), " ")
        text = text.replace(Regex("""\n{3,}"""), "\n\n")
        text = text.replace(Regex("""[ \t]+\n"""), "\n")
        return text.trim()
    }

    /** One line for a list: cleaned, markdown marks removed, newlines folded. */
    fun preview(raw: String, limit: Int = 120): String {
        val key = "$limit\u0001$raw"
        previewCache[key]?.let { return it }
        var text = clean(raw)
        text = text.replace(Regex("""\*\*|__|`"""), "")
        text = text.replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        text = text.replace(Regex("""^[•\-]\s+"""), "")
        text = text.replace(Regex("""\s*\n+\s*[•\-]?\s*"""), " · ")
        text = text.trim()
        if (text.length > limit) text = text.take(limit - 1) + "…"
        if (previewCache.size > 3000) previewCache.clear()
        previewCache[key] = text
        return text
    }

    /** A card summary that is only metadata ("eventId: 8648") is useless as a title: use the note's first sentence. */
    fun cardTitle(summary: String, note: String?): String {
        val s = clean(summary)
        if (s.isNotEmpty() && !isMetadataLine(s)) return s
        val first = clean(note ?: "").split("\n").firstOrNull().orEmpty()
        return first.ifEmpty { "확인이 필요한 일" }
    }

    /** `<knowledge_x> … </knowledge_x>` and similar prompt context the model should never have shown. */
    fun dropPromptBlocks(text: String): String {
        var t = text.replace(Regex("""(?s)<([a-z][a-z0-9_]*)>.*?</\1>"""), "")
        t = t.replace(Regex("""(?s)\n?<[a-z][a-z0-9_]*_[a-z0-9_]*>.*$"""), "")
        Regex("""(?m)^.*params_json.*$""").find(t)?.let { t = t.substring(0, it.range.first) }
        return t
    }

    fun isMetadataLine(line: String) = Regex("""^[a-zA-Z][a-zA-Z0-9_]{1,24}: \S""").containsMatchIn(line)

    /** Everything from the first run of two or more `key: value` lines (eventId, source, received …) is raw context. */
    fun cutMetadata(text: String): String {
        val lines = text.split("\n")
        for (i in 0 until lines.size - 1) {
            if (isMetadataLine(lines[i]) && isMetadataLine(lines[i + 1])) return lines.subList(0, i).joinToString("\n")
        }
        if (lines.size > 1 && isMetadataLine(lines.last())) return lines.dropLast(1).joinToString("\n")
        return text
    }

    val shortcodes = mapOf(
        "white_check_mark" to "✅", "heavy_check_mark" to "✔️", "x" to "❌", "warning" to "⚠️", "rotating_light" to "🚨", "tada" to "🎉",
        "eyes" to "👀", "memo" to "📝", "thumbsup" to "👍", "+1" to "👍", "thumbsdown" to "👎", "-1" to "👎", "fire" to "🔥", "bulb" to "💡",
        "hammer_and_wrench" to "🛠️", "tools" to "🛠️", "gear" to "⚙️", "lock" to "🔒", "bell" to "🔔", "calendar" to "📅", "pushpin" to "📌",
        "red_circle" to "🔴", "green_circle" to "🟢", "yellow_circle" to "🟡", "orange_circle" to "🟠", "large_blue_circle" to "🔵",
        "information_source" to "ℹ️", "no_entry" to "⛔", "stop_sign" to "🛑", "rocket" to "🚀", "speech_balloon" to "💬", "art" to "🎨",
        "test_tube" to "🧪", "shield" to "🛡️", "money_with_wings" to "💸", "moneybag" to "💰", "clipboard" to "📋", "mag" to "🔍",
        "hourglass" to "⌛", "zzz" to "💤", "wave" to "👋", "pray" to "🙏", "sparkles" to "✨", "star" to "⭐", "heart" to "❤️",
    )

    /** Discord leftovers: `<@123>`, `<#123>`, `<:name:123>`, `<t:1700000000:R>`, `:emoji:`, `||spoiler||`, `-# `. */
    fun discord(text: String, zone: ZoneId): String {
        var t = text
        t = t.replace(Regex("""<@[!&]?\d+>\s?"""), "")
        t = t.replace(Regex("""<#\d+>"""), "")
        t = t.replace(Regex("""<a?:\w+:\d+>"""), "")
        t = Regex("""<t:(\d{9,11})(?::[a-zA-Z])?>""").replace(t) { m ->
            val secs = m.groupValues[1].toLongOrNull() ?: return@replace ""
            val date = Instant.ofEpochSecond(secs)
            "${Format.monthDay(date, zone)} ${Format.clock(date, zone)}"
        }
        t = Regex("""(?<![\w:/]):([a-z0-9_+\-]{2,30}):(?![\w/])""").replace(t) { m -> shortcodes[m.groupValues[1]] ?: "" }
        t = t.replace(Regex("""\|\|(.+?)\|\|"""), "$1")
        t = t.replace(Regex("""(?m)^-# """), "")
        // The owner's channels are gone from the app: "→ #결재" means the 결재 tab.
        t = t.replace("#결재", "결재 탭")
        t = t.replace(Regex("""(?<![\w#&])#([가-힣]{1,8}?)(?=(?:에서|으로|에|을|를|이|가|은|는|의|로)?(?:[\s,.)!?]|$))"""), "$1 방")
        t = t.replace("방 방", "방")
        return t
    }

    /** Block markdown the chat bubble cannot draw: headings → bold line, quotes → plain, code fences → gone, `- ` → `• `. */
    fun plainLines(text: String): String {
        val out = mutableListOf<String>()
        for (raw in text.split("\n")) {
            var line = raw
            if (line.trim().startsWith("```")) continue
            val h = Regex("""^\s{0,3}#{1,6}\s+(.*)$""").find(line)
            val q = Regex("""^\s{0,3}>\s?(.*)$""").find(line)
            val b = Regex("""^(\s*)[-*]\s+(.*)$""").find(line)
            when {
                h != null -> { val body = h.groupValues[1].trim(); line = if (body.isEmpty()) "" else if (body.startsWith("**")) body else "**$body**" }
                q != null -> line = q.groupValues[1]
                b != null -> line = b.groupValues[1] + "• " + b.groupValues[2]
                Regex("""^\s*([-*_]\s*){3,}$""").matches(line) -> continue
            }
            out.add(line)
        }
        return out.joinToString("\n")
    }

    /** Branch names, run ids and hashes mean nothing to the owner. */
    fun dropInternalIds(text: String): String {
        var t = text
        t = t.replace(Regex("""\s*\((?:head|commit|작업|run|id)\s+[0-9a-f]{6,40}\)"""), "")
        t = t.replace(Regex("""\s*·\s*[A-Za-z0-9][A-Za-z0-9._/:-]*[0-9a-f]{8,}[A-Za-z0-9._/:-]*(?=\s*(·|\n|$))"""), "")
        t = t.replace(Regex("""\s*·\s*[a-z0-9-]+/[A-Za-z0-9._-]+-\d{4}-\d{2}-\d{2}(?=\s*(·|\n|$))"""), "")
        return t
    }

    /** Internal English role words that slipped into sentences; the same emoji twice in a row once. */
    fun glossary(text: String): String {
        var t = text.replace(Regex("""([\x{2600}-\x{27BF}\x{1F300}-\x{1FAFF}]\x{FE0F}?)(\s*\1)+"""), "$1")
        for ((en, ko) in listOf("Engineer" to "개발 봇", "P0 결정" to "긴급 결정")) {
            t = t.replace(Regex("""(?<![A-Za-z])${Regex.escape(en)}(?![A-Za-z])"""), ko)
        }
        return t
    }
}

/** A bot talking to another bot, from the old notice wording ("💬 Leo → Mia · 검토 요청: …") or `author` + `to`. */
data class BotTalk(val from: String, val to: String, val label: String?, val text: String, val isReply: Boolean) {
    companion object {
        private val memo = ConcurrentHashMap<String, Any>()
        private val NONE = Any()
        private val pattern = Regex("""(?s)^\s*(💬|↩️|↩︎|↪️)?\s*([^\s→·:]{1,20}(?: [^\s→·:]{1,20})?)\s*(?:→|->)\s*([^\s→·:]{1,20}(?: [^\s→·:]{1,20})?)\s*·\s*([^:\n]{1,24}):\s*(.+)$""")

        fun parse(raw: String): BotTalk? {
            memo[raw]?.let { return if (it === NONE) null else it as BotTalk }
            val m = pattern.find(raw)
            val out = m?.let {
                val mark = it.groupValues[1]
                val label = it.groupValues[4].trim()
                BotTalk(it.groupValues[2], it.groupValues[3], label.ifEmpty { null }, it.groupValues[5].trim(), mark.startsWith("↩") || label.startsWith("답"))
            }
            if (memo.size >= 4000) memo.clear()
            memo[raw] = out ?: NONE
            return out
        }
    }
}
