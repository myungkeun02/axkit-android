package dev.axkit.core

/**
 * `@` in the chat box: while the word at the caret starts with `@`, bot names are offered; picking one puts
 * "@봇이름 " there. Pure, so the word finding and the insert are unit-tested.
 */
object Mention {
    /** The `@word` the caret is in: where the `@` is and what follows it up to the caret. */
    data class Query(val start: Int, val end: Int, val text: String)

    data class Inserted(val text: String, val caret: Int)

    /** Null unless the caret sits right after `@…` that starts the text or follows a space (so mail addresses don't count). */
    fun query(value: String, caret: Int): Query? {
        val c = caret.coerceIn(0, value.length)
        var i = c - 1
        while (i >= 0 && value[i] != '@') {
            if (value[i].isWhitespace() || c - i > 20) return null
            i--
        }
        if (i < 0) return null
        if (i > 0 && !value[i - 1].isWhitespace()) return null
        return Query(i, c, value.substring(i + 1, c))
    }

    /** Names that start with (then contain) the typed part; 초성 works too ("ㅈㄱ" → 재고 팀장). At most `limit`. */
    fun suggest(names: List<String>, typed: String, limit: Int = 6): List<String> {
        val unique = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (typed.isBlank()) return unique.take(limit)
        val q = KoreanSearch.normalize(typed)
        val starts = unique.filter { KoreanSearch.normalize(it).startsWith(q) }
        val rest = unique.filter { it !in starts && KoreanSearch.matches(it, typed) }
        return (starts + rest).take(limit)
    }

    /** Replaces the `@word` with "@name " (no doubled space if one already follows). */
    fun insert(value: String, q: Query, name: String): Inserted {
        val after = value.substring(q.end)
        val tail = if (after.startsWith(" ")) after.substring(1) else after
        val word = "@$name "
        return Inserted(value.substring(0, q.start) + word + tail, q.start + word.length)
    }
}
