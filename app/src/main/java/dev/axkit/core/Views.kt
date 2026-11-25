package dev.axkit.core

import kotlinx.serialization.Serializable

// `GET views/<name>` payloads (APP-PROTOCOL §5, §7–§15). Mirrors AxKitCore/Views.swift.

@Serializable
data class TodayView(
    val date: String = "",
    val events: List<Event>? = null,
    val todos: List<Todo>? = null,
    val pendingCards: Int? = null,
    /** Chats and mails waiting for the owner's answer, newest first (null when neither source is on). */
    val replies: List<Reply>? = null,
    /** Today's spending (only with the spending skill). */
    val spend: Spend? = null,
) {
    @Serializable data class Reply(val from: String = "", val text: String = "", val source: String? = null, val at: String? = null)
    @Serializable data class Spend(val amount: Double = 0.0, val count: Int = 0)
    /** §36: [calendar] is the calendar name, sent when there are two or more. */
    @Serializable data class Event(val title: String, val start: String, val end: String? = null, val allDay: Boolean? = null, val calendar: String? = null)
    @Serializable data class Todo(val text: String, val due: String? = null, val source: String? = null)
}

@Serializable
data class BotsView(val main: Main? = null, val groups: List<Group> = emptyList()) {
    @Serializable
    data class Bot(
        val id: String,
        val name: String,
        val role: String? = null,
        val icon: String? = null,
        val avatar: String? = null,
        val channel: String,
        val status: String? = null,
        val statusText: String? = null,
        val isNew: Boolean? = null,
        /** R16: `lead` · `staff` · `reviewer`, its team and AI grade (S/A/B/C). */
        val rank: String? = null,
        val team: String? = null,
        val tier: String? = null,
        /** §16: how this bot works (its role prompt, ≤1500 chars) and who wrote it: `default` · `ai` · `owner`. */
        val instructions: String? = null,
        val instructionsBy: String? = null,
    )

    @Serializable
    data class Team(
        val id: String,
        val name: String,
        val purpose: String? = null,
        val channel: String,
        val kind: String? = null,
        val members: List<String> = emptyList(),
        val decidedBy: String? = null,
        val reason: String? = null,
    )

    @Serializable
    data class Group(
        val id: String,
        val label: String,
        val summary: String? = null,
        val icon: String? = null,
        val bots: List<Bot> = emptyList(),
        val meetingChannel: String? = null,
        val teams: List<Team>? = null,
    )

    @Serializable data class Main(val name: String, val subtitle: String? = null)

    val allBots: List<Bot> get() = groups.flatMap { it.bots }
    val allTeams: List<Team> get() = groups.flatMap { it.teams.orEmpty() }
    /** The bot that speaks for a room: its lead when the room is a team's. */
    fun botForChannel(channel: String): Bot? =
        allBots.firstOrNull { it.channel == channel && it.rank == "lead" } ?: allBots.firstOrNull { it.channel == channel }
    fun teamForChannel(channel: String): Team? = allTeams.firstOrNull { it.channel == channel }
    fun groupForMeeting(channel: String): Group? = groups.firstOrNull { it.meetingChannel == channel }
    fun bot(id: String): Bot? = allBots.firstOrNull { it.id == id }
    fun members(team: Team): List<Bot> = team.members.mapNotNull { bot(it) }
}

@Serializable
data class DocGraphView(
    val layers: List<Layer> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val links: List<Link> = emptyList(),
    /** §21.7: the same documents as a list — layer → item → related items. */
    val tree: List<TreeLayer>? = null,
) {
    @Serializable
    data class TreeLayer(val id: String, val label: String, val summary: String? = null, val count: Int? = null, val items: List<Item> = emptyList()) {
        @Serializable
        data class Item(val id: String, val title: String, val summary: String? = null, val updatedAt: IsoInstant? = null, val related: List<Related>? = null)
        @Serializable data class Related(val id: String, val title: String, val label: String? = null)
    }
    @Serializable data class Layer(val id: String, val label: String, val summary: String? = null)
    @Serializable
    data class Node(
        val id: String,
        val title: String,
        val layer: String,
        val group: String? = null,
        val summary: String? = null,
        val updatedAt: IsoInstant? = null,
        val weight: Double? = null,
    )
    @Serializable data class Link(val from: String, val to: String, val label: String? = null)
}

/** One document. The knowledge view answers with `name` instead of `id` (and `kind` instead of `layer`). */
@Serializable
data class DocumentDetail(
    @kotlinx.serialization.SerialName("id") val rawId: String? = null,
    val name: String? = null,
    @kotlinx.serialization.SerialName("title") val rawTitle: String? = null,
    @kotlinx.serialization.SerialName("layer") val rawLayer: String? = null,
    val kind: String? = null,
    val markdown: String = "",
    val updatedAt: IsoInstant? = null,
    val related: List<Related>? = null,
    /** §21.6: lines that match `q` (line numbers of `markdown`, from 1). */
    val matches: List<Match>? = null,
) {
    @Serializable data class Related(val id: String, val title: String)
    @Serializable data class Match(val line: Int)
    val id: String get() = rawId ?: name ?: ""
    val title: String get() = rawTitle ?: name ?: "문서"
    val layer: String? get() = rawLayer ?: kind
}

@Serializable data class DocumentEnvelope(val document: DocumentDetail? = null)

@Serializable
data class CalendarView(
    val month: String = "",
    val days: List<Day> = emptyList(),
    /** §36: the owner's calendars (legend and the add picker); null from an older Mac. */
    val calendars: List<Calendar>? = null,
    /** §36: true when the Google sign-in can read only the main calendar. */
    val calendarsLimited: Boolean? = null,
) {
    @Serializable
    data class Event(
        val id: String, val title: String, val start: IsoInstant, val end: IsoInstant? = null, val allDay: Boolean? = null, val place: String? = null,
        /** §36: calendar name (two or more calendars) and Google account (two or more accounts). */
        val calendar: String? = null, val account: String? = null,
    )
    @Serializable
    data class Calendar(
        val id: String, val name: String, val primary: Boolean = false, val writable: Boolean = false,
        val description: String? = null, val account: String? = null,
    )
    @Serializable
    data class Done(val id: String, val title: String, val at: IsoInstant, val bot: Author? = null, val detail: String? = null)
    @Serializable
    data class Day(val date: String, val events: List<Event> = emptyList(), val done: List<Done> = emptyList())
    fun day(ymd: String): Day? = days.firstOrNull { it.date == ymd }
}

@Serializable
data class JobsView(val groups: List<Group> = emptyList()) {
    @Serializable data class LastRun(val startedAt: IsoInstant? = null, val ok: Boolean? = null, val error: String? = null)
    @Serializable
    data class Job(
        val id: String,
        val name: String,
        val detail: String? = null,
        val scheduleLabel: String? = null,
        val enabled: Boolean = true,
        val running: Boolean? = null,
        val lastRun: LastRun? = null,
        val nextRunAt: IsoInstant? = null,
        val source: String? = null,
        /** R6: whether the app may switch it (missing = yes for `source: "ax"`). */
        val toggleable: Boolean? = null,
    ) { val canToggle: Boolean get() = toggleable ?: (source == null || source == "ax") }
    @Serializable data class Group(val id: String, val label: String, val jobs: List<Job> = emptyList())
}

@Serializable
data class SpendingView(
    val month: String = "",
    val currency: String = "KRW",
    val total: Double = 0.0,
    val count: Int = 0,
    val byCategory: List<Category> = emptyList(),
    val previousTotal: Double? = null,
    val budget: Double? = null,
    val entries: List<Entry>? = null,
    val categories: List<CategoryChoice>? = null,
    val subscriptions: List<Subscription>? = null,
) {
    @Serializable data class Category(val category: String, val label: String, val amount: Double, val count: Int = 0)
    @Serializable
    data class Entry(
        val id: String,
        val at: IsoInstant? = null,
        val date: String = "",
        val merchant: String = "",
        val amount: Double = 0.0,
        val category: String? = null,
        val categoryLabel: String? = null,
        val note: String? = null,
        val source: String? = null,
    )
    @Serializable data class CategoryChoice(val id: String, val label: String)
    @Serializable data class Subscription(val key: String, val merchant: String, val cadence: String, val amount: Double, val nextDate: String? = null)
}

@Serializable
data class UsageView(
    val period: String = "",
    val totals: Totals = Totals(),
    val byModel: List<ModelRow> = emptyList(),
    val byPurpose: List<PurposeRow>? = null,
    val byBot: List<BotRow>? = null,
    val daily: List<Point>? = null,
    val budget: Budget? = null,
) {
    @Serializable data class Totals(val calls: Int = 0, val failures: Int = 0, val costUsd: Double = 0.0, val inputTokens: Int? = null, val outputTokens: Int? = null)
    @Serializable data class ModelRow(val providerId: String, val model: String, val calls: Int = 0, val failures: Int = 0, val costUsd: Double = 0.0)
    @Serializable data class PurposeRow(val purpose: String, val calls: Int = 0, val costUsd: Double = 0.0)
    @Serializable data class BotRow(val botId: String, val name: String, val calls: Int = 0, val costUsd: Double = 0.0)
    @Serializable data class Point(val date: String, val calls: Int = 0, val costUsd: Double = 0.0)
    @Serializable data class Budget(val dailyUsd: Double = 0.0, val monthlyUsd: Double = 0.0)
}

@Serializable
data class ProvidersView(val providers: List<Provider> = emptyList()) {
    @Serializable
    data class Provider(
        val id: String,
        val label: String,
        /** `api_key` · `subscription` · `local`. */
        val kind: String = "api_key",
        val connected: Boolean = false,
        val status: String? = null,
        val detail: String? = null,
        val keyHint: String? = null,
        val help: String? = null,
        val signupUrl: String? = null,
        val models: List<String>? = null,
    )
}

@Serializable
data class HardwareView(
    val checkedAt: IsoInstant? = null,
    val machine: Machine = Machine("맥"),
    val cpuPercent: Double? = null,
    val memory: Meter? = null,
    val disk: Meter? = null,
    /** `nominal` · `fair` · `serious` · `critical`. */
    val thermal: String? = null,
    val power: String? = null,
    val network: Map<String, String>? = null,
    val server: Map<String, String>? = null,
    val summary: String? = null,
) {
    @Serializable data class Machine(val name: String, val model: String? = null, val os: String? = null, val uptimeSec: Double? = null)
    @Serializable data class Meter(val used: Double, val total: Double, val unit: String = "GB")
}

/** R9: server-drawn screens. */
@Serializable
data class BlocksView(val title: String = "", val blocks: List<Block> = emptyList()) {
    /** §34: a row with [message] can be tapped; it sends that sentence to the chat, like an `ask` block. */
    @Serializable data class Row(val title: String, val detail: String? = null, val trailing: String? = null, val message: String? = null) {
        val tapMessage: String? get() = message?.trim()?.takeIf { it.isNotEmpty() }
    }
    @Serializable
    data class Block(
        /** `text` · `stat` · `list` · `note` · `ask` (a button that sends `message` to the main chat). */
        val kind: String,
        val title: String? = null,
        val text: String? = null,
        val value: String? = null,
        val rows: List<Row>? = null,
        val message: String? = null,
    )
}

/** R11: things the assistant now does without asking. */
@Serializable
data class AutonomyView(val rules: List<Rule> = emptyList(), val alwaysAsk: List<String>? = null) {
    @Serializable
    data class Rule(val id: String, val label: String, val detail: String? = null, val since: IsoInstant? = null, val uses: Int? = null)
}

@Serializable
data class ModelTiersView(
    val tiers: List<Tier> = emptyList(),
    val purposes: List<Purpose> = emptyList(),
    val changes: List<Change>? = null,
    val changedAt: IsoInstant? = null,
) {
    @Serializable data class Model(val id: String, val label: String, val provider: String = "", val note: String? = null, val available: Boolean? = null)
    @Serializable data class Tier(val id: String, val label: String, val summary: String? = null, val models: List<Model> = emptyList())
    @Serializable data class Purpose(val id: String, val label: String, val detail: String? = null, val tier: String, val calls7d: Int? = null)
    @Serializable data class Change(val text: String, val at: IsoInstant? = null)
}

/** §17: things the assistant or a bot does on a schedule. New ones are made by asking in the chat. */
@Serializable
data class RoutinesView(val routines: List<Routine> = emptyList(), val followUps: List<FollowUp>? = null, val limit: Int? = null) {
    @Serializable
    data class Routine(
        val id: String,
        val title: String,
        val instruction: String? = null,
        val scheduleLabel: String? = null,
        val bot: Author? = null,
        val enabled: Boolean = true,
        val lastRun: IsoInstant? = null,
        val lastResult: String? = null,
        val lastOk: Boolean? = null,
    )
    @Serializable data class FollowUp(val bot: Author? = null, val what: String, val dueAt: IsoInstant? = null)
}

/** §7.2 (ax-hq): rooms such as 결재·브리핑·장애·로그 — some read-only. */
@Serializable
data class ChannelsView(val channels: List<Channel> = emptyList()) {
    @Serializable
    data class Channel(
        val id: String,
        val label: String,
        val kind: String? = null,
        val group: String? = null,
        /** false = the owner can only read this room; no input box. */
        val writable: Boolean? = null,
        val lastSeq: Int? = null,
        val count: Int? = null,
        /** §21.2: who speaks for the room. */
        val lead: Author? = null,
        val preview: String? = null,
        val unread: Int? = null,
        /** §21.4: read up to this seq (on any device). */
        val readSeq: Int? = null,
    )
}

/** ax-hq §7: documents that build up from conversations, grouped by area. */
@Serializable
data class KnowledgeView(val areas: List<Area> = emptyList()) {
    @Serializable data class Doc(val id: String, val title: String, val summary: String? = null, val updatedAt: IsoInstant? = null, val lines: Int? = null)
    /** `total`: every document in the area (the list holds the newest 300; the rest are found by search). */
    @Serializable data class Area(val id: String, val label: String, val summary: String? = null, val total: Int? = null, val docs: List<Doc> = emptyList())

    /** Newest first across every area (a document can sit in more than one area; shown once). */
    fun recent(limit: Int): List<Doc> =
        areas.flatMap { it.docs }.distinctBy { it.id }.sortedByDescending { it.updatedAt ?: java.time.Instant.MIN }.take(limit)
}

/** §21.6 `GET views/knowledge?q=`: the Mac's own search through every document. */
@Serializable
data class KnowledgeSearchView(val query: String? = null, val total: Int? = null, val results: List<Result>? = null) {
    @Serializable
    data class Result(
        val id: String, val title: String, val area: String? = null, val areaLabel: String? = null,
        val updatedAt: IsoInstant? = null, val snippet: String? = null, val line: Int? = null,
    )
}

/** ax-hq §7: safe checks only, like `ax doctor`. ax-hq sends counts instead of `summary`; the screen works it out. */
@Serializable
data class HealthView(
    val checkedAt: IsoInstant? = null,
    @kotlinx.serialization.SerialName("summary") val rawSummary: kotlinx.serialization.json.JsonElement? = null,
    val checks: List<Check> = emptyList(),
) {
    val summary: String? get() = (rawSummary as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    @Serializable
    data class Check(
        val id: String,
        @kotlinx.serialization.SerialName("label") val rawLabel: String,
        @kotlinx.serialization.SerialName("status") val rawStatus: String = "unknown",
        @kotlinx.serialization.SerialName("detail") val rawDetail: String? = null,
        /** ax-hq: what the check belongs to ("기본", "QA" …). */
        val group: String? = null,
    ) {
        /** Command output arrives with column padding ("node    v24"): one space is enough. */
        val label: String get() = rawLabel.replace(Regex("\\s{2,}"), " ")
        /** `ok` · `warn` · `error` (`fail` is read as `error`; anything else is shown as unknown). */
        val status: String get() = if (rawStatus == "fail" || rawStatus == "failed") "error" else rawStatus
        val detail: String? get() = rawDetail?.trim()?.ifEmpty { null }
    }
}

/** R14. */
@Serializable
data class RecordingsView(val recordings: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val id: String,
        val title: String? = null,
        val startedAt: IsoInstant? = null,
        val durationSec: Double? = null,
        /** `receiving` · `transcribing` · `summarizing` · `done` · `failed`. */
        val status: String = "receiving",
        val statusText: String? = null,
        val documentId: String? = null,
        val driveUrl: String? = null,
    )
}

@Serializable
data class ViewInfo(
    val name: String,
    val version: Int = 1,
    val actions: List<String>? = null,
    /** R9: server-drawn screens, shown under 더보기 › 새 기능. */
    val title: String? = null,
    val icon: String? = null,
    val ui: String? = null,
)

@Serializable
data class Capabilities(
    @kotlinx.serialization.SerialName("protocol") val protocolVersion: Int = 1,
    val features: List<String> = emptyList(),
    val views: List<ViewInfo> = emptyList(),
) {
    fun has(feature: String) = features.contains(feature)
    fun hasView(name: String) = views.any { it.name == name }
    fun hasAction(view: String, action: String) = views.firstOrNull { it.name == view }?.actions?.contains(action) == true
    fun view(name: String) = views.firstOrNull { it.name == name }
}

@Serializable
data class ServerStatus(
    val assistant: Assistant = Assistant("비서"),
    val lastSeq: Int = 0,
    val serverTime: IsoInstant? = null,
    /** R10: the global stop switch. */
    val stopped: Boolean? = null,
) { @Serializable data class Assistant(val name: String, val language: String? = null, val timezone: String? = null) }
