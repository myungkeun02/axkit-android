package dev.axkit.core

/**
 * Finding documents the way people type in Korean: spaces and case ignored, part of a word is enough, marks like
 * / · - too, and a query of initial consonants only ("ㄱㅈ") matches by first sounds ("결제"). Port of KoreanSearch.swift.
 */
object KoreanSearch {
    private val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toList()

    fun normalize(text: String): String = text.lowercase().filter { it.isLetter() || it.isDigit() }

    fun initialSounds(text: String): String = buildString {
        for (c in normalize(text)) if (c.code in 0xAC00..0xD7A3) append(initials[(c.code - 0xAC00) / 588]) else append(c)
    }

    fun isInitialsOnly(query: String): Boolean { val q = normalize(query); return q.isNotEmpty() && q.all { it in initials } }

    fun matches(text: String, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return if (isInitialsOnly(q)) initialSounds(text).contains(q) else normalize(text).contains(q)
    }

    /** Where the query sits in `text` (ignoring spaces), as a range of the original text — for highlighting. */
    fun range(text: String, query: String): IntRange? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val initialsOnly = isInitialsOnly(q)
        val kept = mutableListOf<Pair<Char, Int>>()
        text.forEachIndexed { i, ch ->
            if (ch.isLetter() || ch.isDigit()) {
                var key = ch.lowercaseChar()
                if (initialsOnly && ch.code in 0xAC00..0xD7A3) key = initials[(ch.code - 0xAC00) / 588]
                kept.add(key to i)
            }
        }
        if (q.length > kept.size) return null
        for (start in 0..(kept.size - q.length)) {
            if (q.indices.all { kept[start + it].first == q[it] }) return kept[start].second..kept[start + q.length - 1].second
        }
        return null
    }

    /** The line of a document that contains the query, trimmed around the hit — shown under a result. */
    fun snippet(body: String, query: String, width: Int = 70): String? {
        for (raw in body.split("\n")) {
            val line = raw.trim().replace(Regex("""^[#>*\-•\s]+"""), "").replace("**", "")
            val r = range(line, query) ?: continue
            val before = r.first
            if (line.length <= width || before < width / 3) return if (line.length > width) line.take(width) + "…" else line
            val tail = line.substring(r.first - width / 3)
            return "…" + if (tail.length > width) tail.take(width) + "…" else tail
        }
        return null
    }
}
