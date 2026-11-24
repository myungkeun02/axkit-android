package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** §24 `GET views/team-plan`: the team structure the first look proposes, before anything is built. */
@Serializable
data class TeamPlanView(val plan: Plan? = null) {
    companion object {
        /**
         * Decodes `data`, keeping each team's, role's and routine's original object so a save sends back the fields this
         * app does not know (a newer server's) unchanged.
         */
        fun decode(data: JsonElement): TeamPlanView {
            val view = ProtocolJson.decodeFromJsonElement(serializer(), data)
            val plan = view.plan ?: return view
            val rawTeams = ((data as? JsonObject)?.get("plan") as? JsonObject)?.get("teams") as? JsonArray ?: return view
            fun arr(o: JsonObject?, key: String) = o?.get(key) as? JsonArray
            return TeamPlanView(plan.copy(teams = plan.teams.mapIndexed { i, t ->
                val raw = rawTeams.getOrNull(i) as? JsonObject
                t.copy(
                    raw = raw,
                    roles = t.roles.mapIndexed { k, r -> r.copy(raw = arr(raw, "roles")?.getOrNull(k) as? JsonObject) },
                    routines = t.routines.mapIndexed { k, r -> r.copy(raw = arr(raw, "routines")?.getOrNull(k) as? JsonObject) },
                )
            }))
        }
    }

    @Serializable
    data class Plan(
        val id: String,
        val createdAt: IsoInstant? = null,
        val status: String = "proposed",
        val appliedAt: IsoInstant? = null,
        val teams: List<Team> = emptyList(),
    ) {
        val proposed: Boolean get() = status == "proposed"
    }

    @Serializable
    data class Team(
        val company: String = "",
        val name: String = "",
        val purpose: String = "",
        val kind: String = "general",
        val roles: List<Role> = emptyList(),
        val tasks: List<String> = emptyList(),
        val routines: List<Routine> = emptyList(),
        /** The server's own object (see [decode]). */
        @Transient val raw: JsonObject? = null,
    ) {
        val isDev: Boolean get() = kind == "dev"
    }

    @Serializable data class Role(val title: String = "", val does: String = "", @Transient val raw: JsonObject? = null)

    @Serializable data class Routine(
        val title: String = "",
        val instruction: String = "",
        val schedule: Schedule = Schedule(),
        @Transient val raw: JsonObject? = null,
    )

    /** `daily` + at · `weekly` + day + at · `every` + hours. */
    @Serializable data class Schedule(val kind: String = "daily", val day: String? = null, val at: String? = null, val hours: Int? = null)
}

@Serializable data class TeamPlanApplied(val teams: Int = 0, val routines: Int = 0)

/** Edits on a proposed plan, by index. Pure, so the save body and the limits are unit-tested. */
object TeamPlanEdit {
    const val MAX_TEAMS = 24
    const val MAX_ROLES = 6
    const val ROLE_TITLE_MAX = 20
    const val ROLE_DOES_MAX = 120
    const val TEAM_NAME_MAX = 60
    const val COMPANY_MAX = 80
    const val PURPOSE_MAX = 300
    const val ROUTINE_TITLE_MAX = 60
    const val INSTRUCTION_MAX = 1000
    val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val DAY_KO = mapOf("mon" to "월", "tue" to "화", "wed" to "수", "thu" to "목", "fri" to "금", "sat" to "토", "sun" to "일")
    private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun dayWord(day: String?): String = DAY_KO[day] ?: (day ?: "")

    /** "매일 09:00" · "매주 월 09:30" · "3시간마다" (the server's own card words). */
    fun scheduleText(s: TeamPlanView.Schedule): String = when (s.kind) {
        "every" -> "${s.hours ?: 1}시간마다"
        "weekly" -> "매주 ${dayWord(s.day)} ${s.at ?: ""}".trim()
        else -> "매일 ${s.at ?: ""}".trim()
    }

    /** Companies in plan order, each with its teams and their place in the full list. */
    fun companies(teams: List<TeamPlanView.Team>): List<Pair<String, List<IndexedValue<TeamPlanView.Team>>>> =
        teams.withIndex().groupBy { it.value.company }.toList()

    fun removeTeam(teams: List<TeamPlanView.Team>, i: Int) = teams.filterIndexed { k, _ -> k != i }

    fun update(teams: List<TeamPlanView.Team>, i: Int, change: (TeamPlanView.Team) -> TeamPlanView.Team) =
        teams.mapIndexed { k, t -> if (k == i) change(t) else t }

    fun addRole(team: TeamPlanView.Team) =
        if (team.roles.size >= MAX_ROLES) team else team.copy(roles = team.roles + TeamPlanView.Role("새 역할", ""))

    fun validTime(at: String?) = at != null && TIME.matches(at)

    /** What the server would refuse, in plain words; null when it can be saved. */
    fun problem(teams: List<TeamPlanView.Team>): String? {
        if (teams.size > MAX_TEAMS) return "팀은 ${MAX_TEAMS}개까지 만들 수 있어요."
        for (t in teams) {
            val name = t.name.trim()
            if (name.isEmpty() || name.length > TEAM_NAME_MAX) return "팀 이름은 1~${TEAM_NAME_MAX}자로 써 주세요."
            val company = t.company.trim()
            if (company.isEmpty() || company.length > COMPANY_MAX) return "'$name'의 회사 이름은 1~${COMPANY_MAX}자로 써 주세요."
            if (t.purpose.length > PURPOSE_MAX) return "'$name'의 목적은 ${PURPOSE_MAX}자까지 쓸 수 있어요."
            if (t.roles.size > MAX_ROLES) return "'$name'의 역할은 ${MAX_ROLES}개까지예요."
            t.roles.firstOrNull { it.title.trim().isEmpty() || it.title.trim().length > ROLE_TITLE_MAX }?.let {
                return "'$name'의 역할 이름은 1~${ROLE_TITLE_MAX}자로 써 주세요."
            }
            if (t.roles.any { it.does.length > ROLE_DOES_MAX }) return "'$name'의 역할 설명은 ${ROLE_DOES_MAX}자까지예요."
            for (r in t.routines) {
                if (r.title.trim().isEmpty() || r.title.length > ROUTINE_TITLE_MAX) return "'$name'의 루틴 이름은 1~${ROUTINE_TITLE_MAX}자로 써 주세요."
                if (r.instruction.trim().isEmpty() || r.instruction.length > INSTRUCTION_MAX) return "'$name'의 루틴 '${r.title}'에 할 일을 써 주세요."
                val s = r.schedule
                val ok = when (s.kind) {
                    "every" -> (s.hours ?: 0) in 1..24
                    "weekly" -> s.day in DAYS && validTime(s.at)
                    "daily" -> validTime(s.at)
                    else -> false
                }
                if (!ok) return "'$name'의 루틴 '${r.title}' 시간을 확인해 주세요. 시간은 09:30처럼 써요."
            }
        }
        return null
    }

    /** The server's object with this app's fields written over it (unknown fields go back as they came). */
    private fun merged(raw: JsonObject?, mine: JsonObject): JsonObject = if (raw == null) mine else JsonObject(raw + mine)

    /** §24.2 body: the whole list, trimmed the way the server reads it, each object merged over the one the server sent. */
    fun body(teams: List<TeamPlanView.Team>): JsonObject = buildJsonObject {
        put("teams", JsonArray(teams.map { t ->
            merged(t.raw, buildJsonObject {
                put("company", JsonPrimitive(t.company.trim()))
                put("name", JsonPrimitive(t.name.trim()))
                put("purpose", JsonPrimitive(t.purpose))
                put("kind", JsonPrimitive(t.kind))
                put("roles", JsonArray(t.roles.map { r -> merged(r.raw, buildJsonObject { put("title", JsonPrimitive(r.title.trim())); put("does", JsonPrimitive(r.does)) }) }))
                put("tasks", JsonArray(t.tasks.map { JsonPrimitive(it) }))
                put("routines", JsonArray(t.routines.map { r ->
                    merged(r.raw, buildJsonObject {
                        put("title", JsonPrimitive(r.title.trim()))
                        put("instruction", JsonPrimitive(r.instruction.trim()))
                        put("schedule", schedule(r.schedule))
                    })
                }))
            })
        }))
    }

    private fun schedule(s: TeamPlanView.Schedule): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(s.kind))
        when (s.kind) {
            "every" -> put("hours", JsonPrimitive(s.hours ?: 1))
            "weekly" -> { put("day", JsonPrimitive(s.day ?: "mon")); put("at", JsonPrimitive(s.at ?: "09:00")) }
            else -> put("at", JsonPrimitive(s.at ?: "09:00"))
        }
    }

    /** Words after "이대로 만들기". */
    fun appliedWords(r: TeamPlanApplied): String = when {
        r.teams == 0 && r.routines == 0 -> "새로 만든 팀은 없어요. 이미 만든 제안서이거나 같은 이름의 팀이 있어요."
        r.routines > 0 -> "팀 ${r.teams}개와 루틴 ${r.routines}개를 만들었어요."
        else -> "팀 ${r.teams}개를 만들었어요."
    }

    /** The bots-tab card's second line. */
    fun cardLine(plan: TeamPlanView.Plan): String {
        val companies = plan.teams.map { it.company }.distinct().size
        val routines = plan.teams.sumOf { it.routines.size }
        val size = "회사 ${companies}곳 · 팀 ${plan.teams.size}개" + if (routines > 0) " · 루틴 ${routines}개" else ""
        return when (plan.status) {
            "applied" -> "만들었어요 · $size"
            "declined" -> "만들지 않기로 했어요 · $size"
            else -> "$size · 만들기 전에 보고 고칠 수 있어요"
        }
    }

    /** A card the server asks with this topic is the team-plan question (§24). */
    fun isPlanCard(topic: String?): Boolean = topic?.startsWith("bots:team-plan:") == true

    /**
     * "앱에서 고칠게요" on the plan card opens 팀 구조 without answering. Server 870e742 calls it `fix-in-app`; before that
     * it was `edit`, which the server took as the old "fix it" no and dropped the plan, so that id never goes out either.
     */
    fun opensScreen(topic: String?, optionId: String): Boolean = isPlanCard(topic) && (optionId == "fix-in-app" || optionId == "edit")
}
