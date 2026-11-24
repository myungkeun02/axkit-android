package dev.axkit.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** §27 `GET views/first-look`: the first look's progress, then "you are this kind of person" and the proposed structure. */
@Serializable
data class FirstLookView(
    /** `idle` · `running` · `paused` · `done` · `failed`. */
    val status: String = "idle",
    val phase: String? = null,
    val phases: List<Phase> = emptyList(),
    val percent: Double = 0.0,
    val startedAt: IsoInstant? = null,
    val finishedAt: IsoInstant? = null,
    val error: String? = null,
    val result: Result? = null,
) {
    @Serializable data class Phase(val id: String, val label: String = "", val done: Int = 0, val total: Int = 0, val state: String = "waiting")

    @Serializable data class Result(val about: String = "", val companies: List<Company> = emptyList(), val plan: PlanRef? = null)

    @Serializable data class Company(val name: String = "", val relation: String = "employer", val projects: List<Project> = emptyList())

    @Serializable data class Project(val name: String = "", val purpose: String = "", val team: Team = Team(), val routines: List<Routine> = emptyList())

    @Serializable data class Team(val kind: String = "general", val roles: List<TeamPlanView.Role> = emptyList())

    @Serializable data class Routine(val title: String = "", @SerialName("when") val whenText: String = "")

    @Serializable data class PlanRef(val id: String = "", val status: String = "proposed") { val proposed: Boolean get() = status == "proposed" }

    val running: Boolean get() = status == "running"
}

/** §27.2 answer: one line to show and the redrawn result. */
@Serializable
data class FirstLookRevised(val text: String = "", val changed: List<String> = emptyList(), val skipped: List<String> = emptyList(), val result: FirstLookView.Result? = null)

/** The same words as the terminal (`ax-kit onboarding status`). */
object FirstLookWords {
    private val RELATION = mapOf("employer" to "다니는 곳", "own" to "내 사업", "client" to "고객사", "partner" to "협력사", "personal" to "개인")
    private val STATUS = mapOf("idle" to "시작 전", "running" to "읽는 중", "paused" to "멈춤", "done" to "끝", "failed" to "멈춤(오류)")

    fun relation(r: String): String = RELATION[r] ?: r

    /** "프로젝트 문서·대화 기록 읽는 중" while running; else the status word. */
    fun head(v: FirstLookView): String {
        if (v.running) return v.phases.firstOrNull { it.state == "active" }?.let { "${it.label} 읽는 중" } ?: "준비 중"
        return STATUS[v.status] ?: v.status
    }

    /** "폴더 파일 120/400 · 프로젝트 문서·대화 기록 12/40": every phase that has something to read. */
    fun counts(v: FirstLookView): String =
        v.phases.filter { it.total > 0 }.joinToString(" · ") { "${it.label} ${it.done}/${it.total}" }.ifEmpty { "읽을 것을 세는 중…" }

    /** The bar: the server's own percent (100 only when done). */
    fun fraction(v: FirstLookView): Float = (v.percent.coerceIn(0.0, 100.0) / 100.0).toFloat()

    fun elapsed(v: FirstLookView, now: Instant = Instant.now()): Duration {
        val start = v.startedAt ?: return Duration.ZERO
        val end = if (!v.running) v.finishedAt ?: now else now
        return Duration.between(start, end).let { if (it.isNegative) Duration.ZERO else it }
    }

    /** "3분 12초" / "45초" / "1시간 5분". */
    fun elapsedText(d: Duration): String {
        val s = d.seconds
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when { h > 0 -> "${h}시간 ${m}분"; m > 0 -> "${m}분 ${sec}초"; else -> "${sec}초" }
    }

    /** "개발팀: 개발, 검수" / "팀". */
    fun team(p: FirstLookView.Project): String {
        val kind = if (p.team.kind == "dev") "개발팀" else "팀"
        val roles = p.team.roles.joinToString(", ") { it.title }
        return if (roles.isEmpty()) kind else "$kind: $roles"
    }

    /** What the start button says, or null when there is nothing to start. */
    fun startLabel(v: FirstLookView): String? = when (v.status) {
        "idle" -> "알아보기 시작"
        "paused", "failed" -> "알아보기 계속"
        else -> null
    }
}
