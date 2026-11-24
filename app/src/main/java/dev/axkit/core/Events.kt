package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

// App protocol v1 events (docs/APP-PROTOCOL.md §4, §7, §8–§15). Mirrors AxKitCore/Events.swift.

@Serializable data class Choice(val id: String, val label: String)

@Serializable data class Author(val id: String, val name: String, val avatar: String? = null)

@Serializable data class Attachment(val id: String, val name: String, val contentType: String = "application/octet-stream", val size: Int? = null, val url: String)

@Serializable data class EventLink(val label: String, val url: String)

@Serializable
data class CardOption(val id: String, val label: String, val tone: String? = null, val detail: String? = null) {
    /** `primary` · `danger` · `neutral`; anything else is neutral. */
    val toneKind: Tone get() = when (tone) { "primary" -> Tone.Primary; "danger" -> Tone.Danger; else -> Tone.Neutral }
    enum class Tone { Primary, Danger, Neutral }
}

@Serializable data class RememberOffer(val label: String, val detail: String? = null)

@Serializable data class Money(val value: Double, val currency: String)

/** §21.3: a piece of work, a pipeline step or a meeting; its lines share the id and the first one has `start`. */
@Serializable data class EventThread(val id: String, val title: String? = null, val start: Boolean? = null)

/** §21.4: someone reacted to a line. */
@Serializable data class ReactionEvent(val seq: Int, val at: IsoInstant? = null, val target: Int, val emoji: String, val by: Author? = null, val channel: String? = null)

/** §21.4: a room was read up to `upTo` (on another device too). */
@Serializable data class ReadEvent(val seq: Int, val channel: String? = null, val upTo: Int)

@Serializable
data class MessageEvent(
    val seq: Int,
    val at: IsoInstant,
    val role: String = "assistant",
    val text: String = "",
    val choices: List<Choice>? = null,
    val channel: String? = null,
    val author: Author? = null,
    /** R17: who a bot was talking to (bot-to-bot lines in team and meeting rooms). */
    val to: Author? = null,
    val thread: EventThread? = null,
    val attachments: List<Attachment>? = null,
    val imageUrl: String? = null,
    val links: List<EventLink>? = null,
    /** §22: the assistant handed a page to the owner; the app shows a button to that screen. */
    val live: LiveRef? = null,
) { val isOwner: Boolean get() = role == "owner" }

/** §4/§22 `message.live`: which screen to open (`browser-live`), why, and the page. */
@Serializable data class LiveRef(val view: String, val reason: String? = null, val url: String? = null)

@Serializable
data class NoticeEvent(
    val seq: Int,
    val at: IsoInstant,
    val text: String = "",
    val choices: List<Choice>? = null,
    val channel: String? = null,
    val author: Author? = null,
    val attachments: List<Attachment>? = null,
    val imageUrl: String? = null,
    val links: List<EventLink>? = null,
    val thread: EventThread? = null,
    /** What kind of notice (`routines.result`, `errands.seat`, `phone.smishing`, `dev.job` …); some are shown as urgent. */
    val kind: String? = null,
    /** §28: a browser or errand result that stopped at a page the owner finishes (same as a message's `live`). */
    val live: LiveRef? = null,
) {
    /** A seat opened up, or a text looks like smishing: drawn with a warning edge and sent as a notification. */
    val isUrgent: Boolean get() = kind == "errands.seat" || kind == "phone.smishing"
    val isRoutineResult: Boolean get() = kind == "routines.result"
    /** §28: the result of a long job the owner asked for (or it stopped when the Mac restarted). */
    val isWorkResult: Boolean get() = kind in WORK_KINDS
    /** Becomes a notification while the app is not in front (the owner asked for it, or it is urgent). */
    val notifies: Boolean get() = isUrgent || isWorkResult

    companion object { val WORK_KINDS = setOf("dev.job", "browser.task", "errands.task", "work.interrupted") }
}

@Serializable
data class CardEvent(
    val seq: Int,
    val at: IsoInstant,
    val cardId: String,
    val summary: String = "",
    val effects: List<String> = emptyList(),
    val effectLabels: List<String> = emptyList(),
    val note: String? = null,
    val expiresAt: IsoInstant? = null,
    val options: List<CardOption>? = null,
    /** §7.3 (ax-hq): pick-from-list menus. */
    val selects: List<CardSelect>? = null,
    /** §7.3 (ax-hq): an input window (Discord modal); `secret` fields such as a P0 PIN are typed hidden. */
    val form: CardForm? = null,
    /** `false` = buttons only (§7.3). Missing means a free-text answer is allowed when the server has `card-reply`. */
    val freeText: Boolean? = null,
    val amount: Money? = null,
    val merchant: String? = null,
    /** R11: the Mac offers to stop asking about this kind of thing. */
    val remember: RememberOffer? = null,
    /** R15: two cards with the same topic are the same question; only the newest shows. */
    val topic: String? = null,
    val channel: String? = null,
    val author: Author? = null,
    val attachments: List<Attachment>? = null,
    val imageUrl: String? = null,
) {
    val isPayment: Boolean get() = effects.contains("pay") || amount != null
    /** Same question = same topic, or (without one) the same context and question text. */
    val questionKey: String get() = topic ?: "${note ?: ""}\u001F$summary"
}

@Serializable
data class CardForm(val id: String, val title: String? = null, val fields: List<Field> = emptyList()) {
    @Serializable
    data class Field(
        val id: String,
        val label: String,
        val value: String? = null,
        val multiline: Boolean? = null,
        val required: Boolean? = null,
        val secret: Boolean? = null,
    )
}

@Serializable
data class CardSelect(val id: String, val placeholder: String? = null, val min: Int? = null, val max: Int? = null, val choices: List<Choice> = emptyList()) {
    @Serializable data class Choice(val id: String, val label: String, val description: String? = null)
}

@Serializable
data class CardClosedEvent(
    val seq: Int,
    val at: IsoInstant,
    val cardId: String,
    val state: String = "closed",
    val answer: String? = null,
    val channel: String? = null,
)

/** The main chat handed work to a specialist bot (R2). */
@Serializable
data class DelegationEvent(
    val seq: Int,
    val at: IsoInstant,
    val bot: Author,
    val botChannel: String,
    val task: String,
    val state: String = "started",
    val summary: String? = null,
    val channel: String? = null,
    /** §21.3: the team lead who handed the work on (missing when the assistant did). */
    val author: Author? = null,
)

sealed interface AppEvent {
    val seq: Int
    /** Room id; "" is the main chat. */
    val channel: String

    data class Message(val e: MessageEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class Notice(val e: NoticeEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class Card(val e: CardEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class CardClosed(val e: CardClosedEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class Delegation(val e: DelegationEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class Reaction(val e: ReactionEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    data class Read(val e: ReadEvent) : AppEvent { override val seq get() = e.seq; override val channel get() = e.channel ?: "" }
    /** A type this app does not know yet (newer server). Kept only for its seq. */
    data class Unknown(val type: String, override val seq: Int) : AppEvent { override val channel get() = "" }
}

object EventDecoding {
    /** One event, or null when it is not an event at all. Unknown or broken shapes become `Unknown` so one bad event never stops the stream. */
    fun event(obj: JsonObject): AppEvent? {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val seq = obj["seq"]?.jsonPrimitive?.intOrNull ?: return null
        val decoded: AppEvent? = runCatching {
            when (type) {
                "message" -> AppEvent.Message(ProtocolJson.decodeFromJsonElement<MessageEvent>(obj))
                "notice" -> AppEvent.Notice(ProtocolJson.decodeFromJsonElement<NoticeEvent>(obj))
                "card" -> AppEvent.Card(ProtocolJson.decodeFromJsonElement<CardEvent>(obj))
                "card_closed" -> AppEvent.CardClosed(ProtocolJson.decodeFromJsonElement<CardClosedEvent>(obj))
                "reaction" -> AppEvent.Reaction(ProtocolJson.decodeFromJsonElement<ReactionEvent>(obj))
                "read" -> AppEvent.Read(ProtocolJson.decodeFromJsonElement<ReadEvent>(obj))
                "delegation" -> AppEvent.Delegation(ProtocolJson.decodeFromJsonElement<DelegationEvent>(obj).let {
                    // Like iOS: an unknown state reads as `started`.
                    if (it.state in setOf("started", "done", "failed")) it else it.copy(state = "started")
                })
                else -> null
            }
        }.getOrNull()
        return decoded ?: AppEvent.Unknown(type, seq)
    }

    fun event(text: String): AppEvent? =
        runCatching { ProtocolJson.parseToJsonElement(text) as? JsonObject }.getOrNull()?.let(::event)

    fun events(array: JsonArray): List<AppEvent> = array.mapNotNull { (it as? JsonObject)?.let(::event) }

    /** The protocol shape again (for the copy kept on the phone); null for kinds that only matter for their seq. */
    fun encode(event: AppEvent): JsonObject? {
        val (type, body) = when (event) {
            is AppEvent.Message -> "message" to ProtocolJson.encodeToJsonElement(MessageEvent.serializer(), event.e)
            is AppEvent.Notice -> "notice" to ProtocolJson.encodeToJsonElement(NoticeEvent.serializer(), event.e)
            is AppEvent.Card -> "card" to ProtocolJson.encodeToJsonElement(CardEvent.serializer(), event.e)
            is AppEvent.CardClosed -> "card_closed" to ProtocolJson.encodeToJsonElement(CardClosedEvent.serializer(), event.e)
            is AppEvent.Delegation -> "delegation" to ProtocolJson.encodeToJsonElement(DelegationEvent.serializer(), event.e)
            else -> return null
        }
        return JsonObject((body as JsonObject) + ("type" to kotlinx.serialization.json.JsonPrimitive(type)))
    }
}
