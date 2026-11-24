package dev.axkit.core

import kotlinx.serialization.Serializable

/** §28 `GET views/work`: long jobs (dev work, browser and life errands) running now and the ones that ended lately. */
@Serializable
data class WorkView(
    val title: String = "하고 있는 일",
    val message: String? = null,
    val active: List<Item> = emptyList(),
    val recent: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(
        val id: String,
        /** `dev` · `browser` · `errand`; other kinds are shown as they are. */
        val kind: String = "",
        val title: String = "",
        /** `queued` · `running` · `waiting` · `done` · `failed` · `interrupted`. */
        val status: String = "running",
        val phase: String? = null,
        val startedAt: IsoInstant? = null,
        val updatedAt: IsoInstant? = null,
        val elapsedMinutes: Int? = null,
        /** Only after it ended. Text read off web pages may be in it: shown as plain text, never acted on. */
        val summary: String? = null,
        val link: String? = null,
        /** The conversation that asked for it (when known). */
        val origin: Origin? = null,
        /** §31: the `id` of the job that handed this one out; shown indented under it. */
        val parent: String? = null,
    ) {
        val isActive: Boolean get() = status in WorkWords.ACTIVE
    }

    @Serializable data class Origin(val channelId: String? = null, val address: String? = null)
}

object WorkWords {
    val ACTIVE = setOf("queued", "running", "waiting")

    fun status(s: String): String = when (s) {
        "queued" -> "차례 기다리는 중"
        "running" -> "하는 중"
        "waiting" -> "직접 하실 차례"
        "done" -> "끝남"
        "failed" -> "못 끝냄"
        "interrupted" -> "중간에 멈춤"
        else -> s
    }

    fun kind(k: String): String = when (k) {
        "dev" -> "개발"
        "bot" -> "봇"
        "browser" -> "브라우저 심부름"
        "errand" -> "생활 심부름"
        else -> k
    }

    /** "12분째" / "1시간 5분째"; for ended ones "40분 걸림". */
    fun minutes(m: Int?, ended: Boolean): String? {
        if (m == null) return null
        val text = if (m >= 60) "${m / 60}시간${if (m % 60 > 0) " ${m % 60}분" else ""}" else "${m}분"
        return if (ended) "$text 걸림" else "${text}째"
    }

    /** "개발 · 테스트 중 · 12분째". */
    fun line(i: WorkView.Item): String {
        val ended = i.status in setOf("done", "failed", "interrupted")
        return listOfNotNull(kind(i.kind).ifBlank { null }, i.phase?.takeIf { it.isNotBlank() && !ended }, minutes(i.elapsedMinutes, ended)).joinToString(" · ")
    }

    /** Where it was asked: "이 앱 대화에서 시킨 일 ›" / "디스코드에서 시킨 일". */
    fun origin(i: WorkView.Item): String? = when (i.origin?.channelId) {
        null -> null
        "channel.app" -> "이 앱 대화에서 시킨 일 ›"
        "channel.discord" -> "디스코드에서 시킨 일"
        "channel.telegram" -> "텔레그램에서 시킨 일"
        else -> "다른 곳에서 시킨 일"
    }

    /** Waiting for the owner (log in, a button, paying) or stopped: worth a second look. */
    fun needsOwner(i: WorkView.Item): Boolean = i.status == "waiting" || i.status == "failed" || i.status == "interrupted"
}

/**
 * §31 작업 패널 (휴대폰: 대화 아래 시트). Same `views/work` data, laid out as a tree: what runs on top, then the last few
 * that ended; a job's sub-jobs go indented right under it, and a parent that ended stays up top while a child still runs.
 */
object WorkPanel {
    const val RECENT = 5

    data class Row(val item: WorkView.Item, val depth: Int)

    data class Layout(val active: List<Row>, val recent: List<Row>, val runningCount: Int) {
        val isEmpty: Boolean get() = active.isEmpty() && recent.isEmpty()
    }

    fun layout(view: WorkView, recent: Int = RECENT): Layout {
        val all = LinkedHashMap<String, WorkView.Item>()
        (view.active + view.recent).forEach { if (it.id !in all) all[it.id] = it }
        val children = all.values.filter { it.parent != null && it.parent != it.id && it.parent in all }.groupBy { it.parent!! }
        fun rootOf(i: WorkView.Item): WorkView.Item {
            var at = i; val seen = mutableSetOf(at.id)
            while (true) { val up = at.parent?.let { all[it] } ?: return at; if (!seen.add(up.id)) return at; at = up }
        }
        fun subtreeActive(i: WorkView.Item, seen: MutableSet<String> = mutableSetOf()): Boolean =
            seen.add(i.id) && (i.isActive || children[i.id].orEmpty().any { subtreeActive(it, seen) })
        fun rows(i: WorkView.Item, depth: Int, out: MutableList<Row>, seen: MutableSet<String>) {
            if (!seen.add(i.id)) return
            out += Row(i, depth)
            // Running children first, each group in the server's order.
            children[i.id].orEmpty().sortedBy { if (subtreeActive(it)) 0 else 1 }.forEach { rows(it, depth + 1, out, seen) }
        }
        // Roots in the server's order (active first: newest started; then recent: newest changed).
        val roots = all.values.map { rootOf(it) }.distinctBy { it.id }
        val seen = mutableSetOf<String>()
        val active = mutableListOf<Row>(); val ended = mutableListOf<Row>()
        roots.filter { subtreeActive(it) }.forEach { rows(it, 0, active, seen) }
        var shown = 0
        for (r in roots.filter { !subtreeActive(it) }) {
            if (shown >= recent) break
            rows(r, 0, ended, seen); shown++
        }
        return Layout(active, ended, all.values.count { it.isActive })
    }

    /** The folded line over the chat box: "하는 중 2개 · 브랜드A 시안 정리"; null hides it. */
    fun strip(view: WorkView?): String? {
        val running = view?.active?.filter { it.isActive }.orEmpty()
        if (running.isEmpty()) return null
        val top = running.firstOrNull { it.parent == null } ?: running.first()
        return "하는 중 ${running.size}개 · ${top.title.ifBlank { WorkWords.kind(top.kind) }}"
    }

    /** Where "열기" goes: `owner` is the main chat (""), `bot:<id>` that bot's room (via its channel); null when unknown. */
    fun room(item: WorkView.Item, channelOfBot: (String) -> String?): String? {
        val address = item.origin?.address ?: return null
        return when {
            address == "owner" -> ""
            address.startsWith("bot:") -> channelOfBot(address.removePrefix("bot:"))
            else -> null
        }
    }
}
