package dev.axkit.core

import java.time.Instant

/** The ordered event log split into rooms. "" is the main chat. Pure value logic, so it is easy to test. */
class Timeline {
    sealed interface Item {
        val seq: Int
        data class Message(val e: MessageEvent) : Item { override val seq get() = e.seq }
        data class Notice(val e: NoticeEvent) : Item { override val seq get() = e.seq }
        /** Cards are looked up by id so a re-sent card (same id, new buttons) replaces the old one in place. */
        data class Card(val id: String, override val seq: Int) : Item
        data class Delegation(val e: DelegationEvent) : Item { override val seq get() = e.seq }
    }


    var lastSeq = 0; private set
    private val seen = HashSet<Int>()
    private val rooms = HashMap<String, MutableList<Item>>()
    private val cards = HashMap<String, CardEvent>()
    private val closed = HashMap<String, CardClosedEvent>()
    /** The newest seq the owner has looked at, per room. */
    private val readUpTo = HashMap<String, Int>()
    /** §21.4: reactions under a line (target seq → emoji → count). */
    private val reactions = HashMap<Int, LinkedHashMap<String, Int>>()

    fun reactions(target: Int): Map<String, Int> = reactions[target] ?: emptyMap()
    fun readUpTo(room: String): Int = readUpTo[room] ?: 0

    /** Returns true when the event was new. */
    fun apply(event: AppEvent): Boolean {
        if (!seen.add(event.seq)) return false
        lastSeq = maxOf(lastSeq, event.seq)
        // Only seqs near the cursor can come again (out of order); older ones need not be remembered forever.
        if (seen.size > 6000) seen.removeAll { it < lastSeq - 3000 }
        val room = event.channel
        when (event) {
            is AppEvent.Message -> append(Item.Message(event.e), room)
            is AppEvent.Notice -> append(Item.Notice(event.e), room)
            is AppEvent.Delegation -> append(Item.Delegation(event.e), room)
            is AppEvent.Card -> {
                val old = cards[event.e.cardId]
                cards[event.e.cardId] = event.e
                // Asked again (same id, new buttons): it moves down to where it was asked now, in the room it is in now.
                if (old != null) rooms[old.channel ?: ""]?.removeAll { it is Item.Card && it.id == event.e.cardId }
                append(Item.Card(event.e.cardId, event.e.seq), room)
            }
            is AppEvent.CardClosed -> closed[event.e.cardId] = event.e
            is AppEvent.Reaction -> {
                reactions.getOrPut(event.e.target) { LinkedHashMap() }.merge(event.e.emoji, 1, Int::plus)
                if (reactions.size > 3000) reactions.keys.sorted().take(reactions.size - 2000).forEach { reactions.remove(it) }
            }
            is AppEvent.Read -> readUpTo[room] = maxOf(readUpTo[room] ?: 0, event.e.upTo)
            is AppEvent.Unknown -> {}
        }
        return true
    }

    fun apply(events: List<AppEvent>) { events.sortedBy { it.seq }.forEach { apply(it) } }

    private fun append(item: Item, room: String) {
        val list = rooms.getOrPut(room) { mutableListOf() }
        val last = list.lastOrNull()
        if (last != null && last.seq > item.seq) {
            val index = list.indexOfFirst { it.seq > item.seq }.let { if (it < 0) list.size else it }
            list.add(index, item)
        } else list.add(item)
        if (list.size > MAX_PER_ROOM) repeat(list.size - MAX_PER_ROOM) { list.removeAt(0) }
    }

    fun items(room: String): List<Item> = rooms[room]?.toList() ?: emptyList()
    val roomIds: List<String> get() = rooms.keys.toList()

    fun card(id: String): CardEvent? = cards[id]
    fun closure(id: String): CardClosedEvent? = closed[id]

    fun isOpen(id: String, now: Instant = Instant.now()): Boolean {
        val card = cards[id] ?: return false
        if (closed[id] != null) return false
        val expires = card.expiresAt
        return !(expires != null && expires.isBefore(now))
    }

    /** Open cards, newest first. The same question never shows twice. */
    fun openCards(now: Instant = Instant.now()): List<CardEvent> {
        val seenKeys = HashSet<String>()
        return cards.values.filter { isOpen(it.cardId, now) }.sortedByDescending { it.seq }.filter { seenKeys.add(it.questionKey) }
    }

    /** An older copy of a question that is asked again in a newer open card. */
    fun isDuplicate(id: String, now: Instant = Instant.now()): Boolean {
        val card = cards[id] ?: return false
        if (!isOpen(id, now)) return false
        // openCards keeps the newest open card per question, so an older one is a duplicate. No sort needed.
        return cards.values.any { it.cardId != id && it.questionKey == card.questionKey && it.seq > card.seq && isOpen(it.cardId, now) }
    }

    /** Closed (or expired) cards, newest first. */
    fun finishedCards(now: Instant = Instant.now(), limit: Int = 20): List<CardEvent> =
        cards.values.filter { !isOpen(it.cardId, now) }.sortedByDescending { it.seq }.take(limit)

    /** After the owner answers, before `card_closed` arrives on the stream (or when the stream is down). */
    fun closeLocally(cardId: String, state: String, answer: String? = null) {
        // A closure made here (seq 0, e.g. "queued") may be replaced by the real answer; one from the Mac stays.
        val before = closed[cardId]
        if (before != null && (before.seq != 0 || before.state != "queued")) return
        val card = cards[cardId] ?: return
        closed[cardId] = CardClosedEvent(0, Instant.now(), cardId, state, answer, card.channel)
    }

    /** §21.4 `views/channels` `readSeq`: read on another device (never moves back). */
    fun setReadUpTo(room: String, seq: Int) { if (seq > (readUpTo[room] ?: 0)) readUpTo[room] = seq }

    /** The copy kept on the phone: the newest [perRoom] lines of each room, the cards they point to (and every open one). */
    fun snapshot(perRoom: Int = 150, skipRoom: (String) -> Boolean = { false }): TimelineSnapshot {
        val events = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val keptCards = HashSet<String>()
        for ((room, list) in rooms) if (!skipRoom(room)) for (item in list.takeLast(perRoom)) when (item) {
            is Item.Message -> EventDecoding.encode(AppEvent.Message(item.e))?.let(events::add)
            is Item.Notice -> EventDecoding.encode(AppEvent.Notice(item.e))?.let(events::add)
            is Item.Delegation -> EventDecoding.encode(AppEvent.Delegation(item.e))?.let(events::add)
            is Item.Card -> keptCards.add(item.id)
        }
        cards.values.filter { it.cardId in keptCards || isOpen(it.cardId) }.forEach { card ->
            EventDecoding.encode(AppEvent.Card(card))?.let(events::add)
            closed[card.cardId]?.let { c -> EventDecoding.encode(AppEvent.CardClosed(c))?.let(events::add) }
        }
        val keptSeqs = events.mapNotNull { (it["seq"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }.toHashSet()
        return TimelineSnapshot(
            lastSeq = lastSeq, readUpTo = HashMap(readUpTo),
            reactions = reactions.entries.filter { it.key in keptSeqs }.associate { it.key.toString() to HashMap(it.value) }, events = events,
        )
    }

    /** A queued answer the Mac refused: the card is open again. */
    fun reopenLocally(cardId: String) { if (closed[cardId]?.seq == 0) closed.remove(cardId) }

    fun markRead(room: String) { readUpTo[room] = items(room).lastOrNull()?.seq ?: readUpTo[room] ?: 0 }

    fun unreadCount(room: String): Int {
        val after = readUpTo[room] ?: 0
        // Items are in seq order: walk back from the newest and stop at what was read.
        var count = 0
        val list = rooms[room] ?: return 0
        for (i in list.indices.reversed()) {
            val item = list[i]
            if (item.seq <= after) break
            if (item is Item.Message && item.e.isOwner) continue
            count++
        }
        return count
    }

    /** Short line for the bot list. */
    fun preview(room: String): Pair<String, Instant>? {
        for (item in items(room).asReversed()) {
            when (item) {
                is Item.Message -> return ChatText.preview(item.e.text) to item.e.at
                is Item.Notice -> return ChatText.preview(item.e.text) to item.e.at
                is Item.Delegation -> return ChatText.preview(item.e.task) to item.e.at
                is Item.Card -> cards[item.id]?.let { return ChatText.preview(ChatText.cardTitle(it.summary, it.note)) to it.at }
            }
        }
        return null
    }

    companion object {
        const val MAX_PER_ROOM = 1000

        /** A timeline from the phone's copy; the stream carries on after its `lastSeq`. */
        fun restore(s: TimelineSnapshot): Timeline {
            val t = Timeline()
            val events = s.events.mapNotNull { EventDecoding.event(it) }.sortedBy { it.seq }
            for (e in events) {
                // Closures made on this phone have seq 0; they go straight in so they never shadow one another.
                if (e is AppEvent.CardClosed) t.closed[e.e.cardId] = e.e else t.apply(e)
            }
            t.lastSeq = maxOf(t.lastSeq, s.lastSeq)
            t.readUpTo.putAll(s.readUpTo)
            s.reactions.forEach { (k, v) -> k.toIntOrNull()?.let { t.reactions[it] = LinkedHashMap(v) } }
            return t
        }
    }

    fun copy(): Timeline {
        val t = Timeline()
        t.lastSeq = lastSeq; t.seen.addAll(seen)
        rooms.forEach { (k, v) -> t.rooms[k] = v.toMutableList() }
        t.cards.putAll(cards); t.closed.putAll(closed); t.readUpTo.putAll(readUpTo)
        reactions.forEach { (k, v) -> t.reactions[k] = LinkedHashMap(v) }
        return t
    }
}

@kotlinx.serialization.Serializable
data class TimelineSnapshot(
    val v: Int = 1,
    val lastSeq: Int = 0,
    val readUpTo: Map<String, Int> = emptyMap(),
    val reactions: Map<String, Map<String, Int>> = emptyMap(),
    val events: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    /** Quick answers picked here (message seq → choice id), so a row stays done after a restart. */
    val chosen: Map<String, String> = emptyMap(),
    /** 👍/👎 given here (seq → emoji). */
    val myReactions: Map<String, String> = emptyMap(),
)

/** The timeline on disk (not secret: the same lines the screen shows; no tokens). Written whole, through a temp file. */
class TimelineStore(private val file: java.io.File) {
    fun load(): Timeline? = loadSnapshot()?.let { runCatching { Timeline.restore(it) }.getOrNull() }

    /** The whole copy may not pass this; fewer lines per room are kept until it fits. */
    val maxBytes = 1_500_000

    fun loadSnapshot(): TimelineSnapshot? = runCatching {
        if (!file.exists()) return null
        ProtocolJson.decodeFromString(TimelineSnapshot.serializer(), file.readText())
    }.getOrNull()

    fun save(snapshot: TimelineSnapshot) = save(listOf(snapshot))

    /** [candidates]: the same copy with fewer lines per room each; the first that fits is written. */
    fun save(candidates: List<TimelineSnapshot>) {
        var text = ""
        for (c in candidates) {
            text = ProtocolJson.encodeToString(TimelineSnapshot.serializer(), c)
            if (text.length * 2 <= maxBytes) break
        }
        // A temp file of its own, renamed over the old copy in one step: a half-written file is never read.
        val tmp = java.io.File.createTempFile("tl-" + file.name, ".tmp", file.parentFile)
        try { tmp.writeText(text); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) } } finally { tmp.delete() }
    }

    fun clear() { file.delete() }
}

/** Plain Korean for card states. */
object CardWords {
    fun closedLabel(closure: CardClosedEvent?, expired: Boolean): String {
        val answer = closure?.answer
        if (!answer.isNullOrEmpty()) return "답함: $answer"
        return when (closure?.state) {
            "approved" -> "승인했어요"
            "denied" -> "안 하기로 했어요"
            "edit" -> "고쳐 달라고 했어요"
            "closed" -> "다른 곳에서 처리했어요"
            "expired" -> "시간이 지나 닫혔어요"
            "queued" -> "맥에 닿으면 답을 보낼게요"
            else -> if (expired) "시간이 지나 닫혔어요" else "끝났어요"
        }
    }

    /** A 결재 in the chat: the question, then why (cleaned, never raw context). */
    fun chatLine(card: CardEvent): String {
        val title = ChatText.cardTitle(card.summary, card.note)
        val note = ChatText.clean(card.note ?: "")
        if (note.isEmpty() || note.startsWith(title)) return note.ifEmpty { title }
        return title + "\n" + note
    }

    /** Buttons when the server sends none: the three classic decisions in easy words. */
    fun defaultOptions(card: CardEvent): List<CardOption> {
        val yes = when {
            card.isPayment -> "네, 결제해요"
            card.effects.contains("send") -> "네, 보내요"
            card.effects.contains("delete") -> "네, 지워요"
            else -> "네, 해 주세요"
        }
        return listOf(
            CardOption("decision:approve", yes, "primary"),
            CardOption("decision:edit", "고쳐서 다시", "neutral"),
            CardOption("decision:deny", "아니요", "danger"),
        )
    }

    /** Turns a button id from `defaultOptions` back into a protocol answer. */
    fun answer(option: CardOption): CardAnswer =
        if (option.id.startsWith("decision:")) CardAnswer.Decision(option.id.removePrefix("decision:")) else CardAnswer.Option(option.id)
}
