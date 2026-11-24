package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** §23 `GET views/projects`: the projects the first look can read, grouped by company. */
@Serializable
data class ProjectsView(
    val companies: List<Company> = emptyList(),
    val picked: Int? = null,
    val scan: Scan? = null,
) {
    @Serializable
    data class Company(val name: String, val projects: List<Project> = emptyList())

    @Serializable
    data class Project(
        val path: String,
        val display: String? = null,
        val name: String,
        val company: String? = null,
        val remote: String? = null,
        val lastActive: IsoInstant? = null,
        val claude: Int? = null,
        val codex: Int? = null,
        val picked: Boolean = false,
    )

    @Serializable
    data class Scan(
        val status: String? = null,
        val phase: String? = null,
        val counts: Map<String, Int>? = null,
        val finishedAt: IsoInstant? = null,
    )
}

/** §23.2 answer: `{"saved":4, "scan":"started"|"running"}`. */
@Serializable
data class ProjectsSaveResult(val saved: Int? = null, val scan: String? = null)

/**
 * The owner's picks and edits on top of the server list. Pure, so the save body and the grouping are unit-tested.
 * Port target: the same rules as §23 (whole list sent, 1–80 character names, at most 200).
 */
data class ProjectsDraft(val rows: List<Row>) {
    data class Row(val project: ProjectsView.Project, val name: String, val company: String, val picked: Boolean)

    companion object {
        const val MAX = 200
        const val NAME_MAX = 80

        fun from(view: ProjectsView): ProjectsDraft = ProjectsDraft(view.companies.flatMap { c ->
            c.projects.map { p -> Row(p, p.name, p.company ?: c.name, p.picked) }
        })
    }

    /** Companies in the order they first appear (the server sends most recent first), each with its rows. */
    val companies: List<Pair<String, List<Row>>>
        get() = rows.groupBy { it.company }.toList()

    val companyNames: List<String> get() = companies.map { it.first }
    val pickedCount: Int get() = rows.count { it.picked }

    fun toggle(path: String) = copy(rows = rows.map { if (it.project.path == path) it.copy(picked = !it.picked) else it })
    fun pickAll(company: String, on: Boolean) = copy(rows = rows.map { if (it.company == company) it.copy(picked = on) else it })

    /** Empty names are ignored (iOS: `guard !n.isEmpty`); long ones are cut at 80. */
    fun rename(path: String, name: String): ProjectsDraft {
        val n = clean(name); if (n.isEmpty()) return this
        return copy(rows = rows.map { if (it.project.path == path) it.copy(name = n) else it })
    }
    fun move(path: String, company: String): ProjectsDraft {
        val c = clean(company); if (c.isEmpty()) return this
        return copy(rows = rows.map { if (it.project.path == path) it.copy(company = c) else it })
    }
    fun renameCompany(from: String, to: String): ProjectsDraft {
        val c = clean(to); if (c.isEmpty()) return this
        return copy(rows = rows.map { if (it.company == from) it.copy(company = c) else it })
    }

    private fun clean(text: String) = text.trim().take(NAME_MAX)

    /** Something the server would refuse, in plain words (null when it can be saved). */
    val problem: String?
        get() = if (pickedCount > MAX) "프로젝트는 ${MAX}개까지 고를 수 있어요." else null

    /** §23.2 body: the whole picked list. */
    fun body(rescan: Boolean): JsonObject = buildJsonObject {
        put("projects", JsonArray(rows.filter { it.picked }.map { r ->
            buildJsonObject {
                put("path", JsonPrimitive(r.project.path))
                put("name", JsonPrimitive(r.name))
                put("company", JsonPrimitive(r.company))
            }
        }))
        put("rescan", JsonPrimitive(rescan))
    }
}

/** Words for §23, the same as iOS `ProjectsView.Scan` and `ProjectsScreen`. */
object ProjectsWords {
    /** "읽는 중 · 프로젝트 읽는 중" and the like. */
    fun status(scan: ProjectsView.Scan?): String {
        val state = when (scan?.status) {
            "running" -> "읽는 중"
            "paused" -> "잠시 멈춤"
            "done" -> "다 읽었어요"
            "failed" -> "읽다가 멈췄어요"
            else -> "아직 읽지 않았어요"
        }
        val phase = phase(scan?.phase)
        return if (scan?.status == "running" && phase != null) "$state · $phase" else state
    }

    fun phase(phase: String?): String? = when (phase) {
        "local" -> "이 맥에서 찾는 중"
        "projects" -> "프로젝트 읽는 중"
        "drive" -> "드라이브 읽는 중"
        "signals" -> "메일·일정 살피는 중"
        "profile" -> "정리하는 중"
        "done" -> "마무리"
        else -> null
    }

    private val countNames = listOf(
        "repos" to "저장소", "files" to "파일", "docs" to "문서", "sessions" to "대화",
        "drive" to "드라이브", "summarized" to "요약", "signals" to "신호",
    )

    /** "저장소 3 · 문서 120 · 대화 45 · 요약 12" (only the non-zero ones). */
    fun counts(counts: Map<String, Int>?): String? {
        val parts = countNames.mapNotNull { (k, label) -> counts?.get(k)?.takeIf { it > 0 }?.let { "$label $it" } }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** "2시간 전 작업 · Claude 12 · Codex 3", or the not-found line. */
    fun detail(p: ProjectsView.Project, now: java.time.Instant = java.time.Instant.now()): String {
        val at = p.lastActive ?: return "지금은 찾지 못한 폴더예요 · 빼기만 할 수 있어요"
        val parts = mutableListOf("${Format.relative(at, now)} 작업")
        p.claude?.takeIf { it > 0 }?.let { parts.add("Claude $it") }
        p.codex?.takeIf { it > 0 }?.let { parts.add("Codex $it") }
        return parts.joinToString(" · ")
    }

    fun saved(result: ProjectsSaveResult, picked: Int): String {
        val n = result.saved ?: picked
        return when (result.scan) {
            "started" -> "${n}개를 저장하고 읽기 시작했어요."
            "running" -> "${n}개를 저장했어요. 지금 읽는 중이라 끝나면 다시 볼게요."
            else -> "${n}개를 저장했어요. 다음 날 새벽에 읽어요."
        }
    }
}
