package dev.axkit.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A room laid out like a group chat: who said it, consecutive lines from one speaker grouped, day separators,
 * replies folded under what they answer, long runs of bot-to-bot talk or work logs folded into one line.
 * Pure value logic so it is tested once and every room looks the same. Port of RoomFeed.swift.
 */
object RoomFeed {
    data class Speaker(val id: String, val name: String, val avatar: String? = null, val isOwner: Boolean = false) {
        companion object { val owner = Speaker("owner", "나", isOwner = true) }
    }

    data class Entry(
        val seq: Int,
        val at: Instant,
        val speaker: Speaker,
        /** Who a bot was talking to. */
        val to: String? = null,
        /** "검토 요청", "질문", "답 (승인)" … from bot talk. */
        val label: String? = null,
        /** Cleaned, ready to draw. */
        val text: String,
        /** A work log line (a team's notice with no author): drawn quieter. */
        val isStatus: Boolean = false,
        val isReply: Boolean = false,
        /** First line of a run from the same speaker: show the face and name. */
        val showHeader: Boolean = true,
        /** The same line sent again right after (shown once with "×2"). */
        val repeats: Int = 1,
        val replies: List<Entry> = emptyList(),
        val source: Timeline.Item,
        /** §21.3: the work thread this line belongs to; the thread's title on its first line. */
        val thread: String? = null,
        val threadTitle: String? = null,
    )

    data class Folded(val id: String, val title: String, val names: List<String>, val entries: List<Entry>)

    sealed interface Row {
        val id: String
        data class Day(val label: String) : Row { override val id get() = "d-$label" }
        data class EntryRow(val entry: Entry) : Row { override val id get() = "e-${entry.seq}" }
        data class FoldedRow(val folded: Folded) : Row { override val id get() = folded.id }
        /** Cards, delegations and the main room's notices: drawn as they always were. */
        data class System(val item: Timeline.Item) : Row { override val id get() = "s-${item.seq}" }
    }

    /** Newest seq in a row (null for a day line). */
    fun seq(row: Row): Int? = when (row) {
        is Row.Day -> null
        is Row.EntryRow -> row.entry.seq
        is Row.FoldedRow -> row.folded.entries.maxOfOrNull { it.seq }
        is Row.System -> row.item.seq
    }

    /**
     * Where "여기까지 읽었어요" goes: before the first row newer than [readUpTo] that someone else wrote, or null when
     * nothing new came (or everything is new: no line above to mark).
     */
    fun readLineIndex(rows: List<Row>, readUpTo: Int): Int? {
        if (readUpTo <= 0) return null
        val i = rows.indexOfFirst { r -> (seq(r) ?: 0) > readUpTo && !(r is Row.EntryRow && r.entry.speaker.isOwner) }
        return i.takeIf { it > 0 }
    }

    private sealed interface Unit_ {
        data class E(val e: Entry) : Unit_
        data class S(val item: Timeline.Item) : Unit_
    }

    /**
     * @param lead who speaks for the room when a line has no author (the team lead, or the room's name).
     * @param assistant the assistant's name (main room).
     */
    fun build(
        items: List<Timeline.Item>, room: String, lead: Speaker?, assistant: String,
        cardDates: Map<String, Instant> = emptyMap(), now: Instant = Instant.now(), zone: ZoneId = Format.seoul, foldAt: Int = 4,
        /**
         * 짧게 보기: replies tucked under what they answer and long runs folded into one line. Off = every line in order
         * with its speaker (team and bot rooms, where the owner wants the whole conversation).
         */
        short: Boolean = true,
    ): List<Row> {
        // 1. Every item → an entry (someone said something) or a system row.
        val roomVoice = lead ?: Speaker(room, assistant)
        fun botSpeaker(author: Author?) =
            if (author != null) Speaker(author.id, author.name, author.avatar) else if (room.isEmpty()) Speaker("assistant", assistant) else roomVoice

        var units = items.map { item ->
            when (item) {
                is Timeline.Item.Message -> {
                    val m = item.e
                    val talk = if (!m.isOwner && m.author == null) BotTalk.parse(m.text) else null
                    when {
                        m.isOwner -> Unit_.E(Entry(m.seq, m.at, Speaker.owner, text = ChatText.clean(m.text), source = item))
                        talk != null -> Unit_.E(entry(talk, m.seq, m.at, item))
                        else -> Unit_.E(Entry(m.seq, m.at, botSpeaker(m.author), to = m.to?.name, text = ChatText.clean(m.text), source = item))
                    }
                }
                is Timeline.Item.Notice -> {
                    val n = item.e
                    val talk = BotTalk.parse(n.text)
                    when {
                        talk != null -> Unit_.E(entry(talk, n.seq, n.at, item))
                        n.author != null -> Unit_.E(Entry(n.seq, n.at, botSpeaker(n.author), text = ChatText.clean(n.text), source = item))
                        room.isNotEmpty() -> Unit_.E(Entry(n.seq, n.at, roomVoice, text = ChatText.clean(n.text), isStatus = true, source = item))
                        // The assistant asking something (buttons) or saying more than a line: its own bubble, like any answer.
                        ChatChoices.noticeIsBubble(n.text, n.choices) -> Unit_.E(Entry(n.seq, n.at, Speaker("assistant", assistant), text = ChatText.clean(n.text), source = item))
                        else -> Unit_.S(item)
                    }
                }
                else -> Unit_.S(item)
            }
        }

        // Threads (§21.3) from the event itself.
        units = units.map { u ->
            if (u !is Unit_.E) u else {
                val t = when (val s = u.e.source) { is Timeline.Item.Message -> s.e.thread; is Timeline.Item.Notice -> s.e.thread; else -> null }
                Unit_.E(u.e.copy(thread = t?.id, threadTitle = t?.title))
            }
        }

        // 2. A thread's later lines fold under its first line; replies fold under the line they answer (A → B, then B → A).
        val merged = mutableListOf<Unit_>()
        val threadRoot = HashMap<String, Int>()
        for (u in units) {
            if (!short) { merged.add(u); continue }
            if (u is Unit_.E && u.e.thread != null) {
                val t = u.e.thread
                val i = threadRoot[t]
                val root = i?.let { merged[it] as? Unit_.E }
                if (i != null && root != null) merged[i] = Unit_.E(root.e.copy(replies = root.e.replies + u.e))
                else { threadRoot[t] = merged.size; merged.add(u) }
                continue
            }
            val e = (u as? Unit_.E)?.e
            if (e == null || !e.isReply || e.to == null) { merged.add(u); continue }
            val target = merged.indices.reversed().take(40).firstOrNull { i ->
                val r = (merged[i] as? Unit_.E)?.e
                r != null && r.speaker.name == e.to && r.to == e.speaker.name && !r.isReply
            }
            if (target != null) {
                val root = (merged[target] as Unit_.E).e
                merged[target] = Unit_.E(root.copy(replies = root.replies + e))
            } else merged.add(u)
        }

        // 3. The same line again right after → one line "×2". Empty lines (all cleaned away) disappear.
        val deduped = mutableListOf<Unit_>()
        for (u in merged) {
            if (u is Unit_.E) {
                val e = u.e
                if (e.text.isEmpty() && !hasMedia(e.source)) continue
                val last = (deduped.lastOrNull() as? Unit_.E)?.e
                if (last != null && last.speaker == e.speaker && last.text == e.text && e.replies.isEmpty()) {
                    deduped[deduped.lastIndex] = Unit_.E(last.copy(repeats = last.repeats + 1)); continue
                }
            }
            deduped.add(u)
        }

        // 4. Long runs of work logs (1), bot-to-bot talk (2) or many lines from one bot (3) fold, keeping the newest two.
        val pieces = mutableListOf<Any>() // Unit_ or Folded
        var run = mutableListOf<Entry>()
        var runKind: Int? = null
        var runSpeaker: Speaker? = null
        fun kind(e: Entry): Int? = when {
            e.isStatus -> 1
            e.to != null && !e.speaker.isOwner -> 2
            room.isNotEmpty() && !e.speaker.isOwner && e.replies.isEmpty() -> 3
            else -> null
        }
        fun flush() {
            if (run.isNotEmpty()) {
                val need = if (!short) Int.MAX_VALUE else if (runKind == 3) foldAt + 2 else foldAt
                if (run.size >= need) {
                    val hidden = run.dropLast(2)
                    val names = mutableListOf<String>()
                    for (e in hidden) for (n in listOfNotNull(e.speaker.name, e.to)) if (n !in names) names.add(n)
                    val title = when (runKind) {
                        1 -> "이전 작업 기록 ${hidden.size}개"
                        3 -> "${hidden[0].speaker.name}의 이전 글 ${hidden.size}개"
                        else -> "${names.take(3).joinToString("·")}${if (names.size > 3) " 외 ${names.size - 3}명" else ""}의 대화 ${hidden.size + hidden.sumOf { it.replies.size }}개"
                    }
                    pieces.add(Folded("f-${hidden[0].seq}", title, names, hidden))
                    run.takeLast(2).forEach { pieces.add(Unit_.E(it)) }
                } else run.forEach { pieces.add(Unit_.E(it)) }
            }
            run = mutableListOf(); runKind = null
        }
        for (u in deduped) {
            val k = (u as? Unit_.E)?.let { kind(it.e) }
            if (u is Unit_.E && k != null) {
                if (runKind != k || (k == 3 && runSpeaker != u.e.speaker)) { flush(); runKind = k; runSpeaker = u.e.speaker }
                run.add(u.e)
            } else { flush(); pieces.add(u) }
        }
        flush()

        // 5. Day lines and faces: a header only when the speaker changes, 10 minutes pass, or a day line sits between.
        val rows = mutableListOf<Row>()
        var lastDay: String? = null
        var lastEntry: Entry? = null
        var lastAt: Instant? = null
        for (p in pieces) {
            val at = when (p) {
                is Folded -> p.entries[0].at
                is Unit_.E -> p.e.at
                is Unit_.S -> date(p.item, cardDates) ?: lastAt ?: now
                else -> now
            }
            lastAt = at
            val day = Format.dayLine(at, now, zone)
            if (day != lastDay) { rows.add(Row.Day(day)); lastDay = day; lastEntry = null }
            when (p) {
                is Folded -> { rows.add(Row.FoldedRow(p)); lastEntry = null }
                is Unit_.S -> { rows.add(Row.System(p.item)); lastEntry = null }
                is Unit_.E -> {
                    var e = p.e
                    val prev = lastEntry
                    if (prev != null && prev.speaker == e.speaker && prev.to == e.to && Duration.between(prev.at, e.at).seconds < 600) e = e.copy(showHeader = false)
                    rows.add(Row.EntryRow(e)); lastEntry = e
                }
            }
        }
        return rows
    }

    private fun entry(talk: BotTalk, seq: Int, at: Instant, item: Timeline.Item) =
        Entry(seq, at, Speaker("bot:" + talk.from, talk.from), to = talk.to, label = talk.label, text = ChatText.clean(talk.text), isReply = talk.isReply, source = item)

    private fun hasMedia(item: Timeline.Item): Boolean = when (item) {
        is Timeline.Item.Message -> !item.e.attachments.isNullOrEmpty() || item.e.imageUrl != null
        is Timeline.Item.Notice -> !item.e.attachments.isNullOrEmpty()
        else -> false
    }

    private fun date(item: Timeline.Item, cardDates: Map<String, Instant>): Instant? = when (item) {
        is Timeline.Item.Message -> item.e.at
        is Timeline.Item.Notice -> item.e.at
        is Timeline.Item.Delegation -> item.e.at
        is Timeline.Item.Card -> cardDates[item.id]
    }
}
