package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.Format
import dev.axkit.core.TeamPlanApplied
import dev.axkit.core.TeamPlanEdit
import dev.axkit.core.TeamPlanView
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 팀 구조 (§24): the company → team → staff role structure (and routines) the first look proposes, before anything is
 * built. While proposed the owner can drop teams, fix names, roles and routines, then build it or drop the plan.
 */
@Composable
fun TeamPlanScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<TeamPlanView>>(Loadable.Loading) }
    var teams by remember { mutableStateOf(emptyList<TeamPlanView.Team>()) }
    var saved by remember { mutableStateOf(emptyList<TeamPlanView.Team>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf<String?>(null) }
    var declining by remember { mutableStateOf(false) }
    var editingTeam by remember { mutableStateOf<Int?>(null) }
    var editingRole by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingRoutine by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var removingTeam by remember { mutableStateOf<Int?>(null) }
    val changed = teams != saved

    fun show(view: TeamPlanView) {
        state = Loadable.Loaded(view)
        teams = view.plan?.teams.orEmpty(); saved = teams
        model.teamPlan = view.plan
    }

    suspend fun load() {
        val client = model.client ?: return
        try { show(TeamPlanView.decode(client.view(kotlinx.serialization.json.JsonElement.serializer(), "team-plan"))) } catch (e: Exception) { state = Loadable.Failed(e.friendly) }
    }

    /** Sends the whole list when something changed; false (with the server's words) when it was refused. */
    suspend fun saveIfChanged(): Boolean {
        if (!changed) return true
        TeamPlanEdit.problem(teams)?.let { error = it; return false }
        val client = model.client ?: return false
        return try {
            client.viewAction("team-plan", "save", TeamPlanEdit.body(teams))
            saved = teams; true
        } catch (e: Exception) { error = e.friendly; false }
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch { try { block() } finally { busy = false } }
    }

    LaunchedEffect(Unit) { load() }

    Screen("팀 구조") {
        LoadableView(state, retry = { load() }) { data ->
            val plan = data.plan
            MoreRefresh(onRefresh = { load() }) {
                Page {
                    if (plan == null) {
                        Panel { Text("지금은 팀 구조 제안이 없어요. 처음 알아보기가 일 지도를 만들면 여기에 보여요.", style = AxType.subheadline, color = Theme.ax.secondary) }
                        return@Page
                    }
                    val editable = plan.proposed
                    StatusPanel(plan, teams)
                    TeamPlanEdit.companies(teams).forEach { (company, list) ->
                        Text(company, style = AxType.title3, color = Theme.ax.label, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                        list.forEach { (i, team) ->
                            TeamPanel(
                                team, editable,
                                editTeam = { editingTeam = i },
                                removeTeam = { removingTeam = i },
                                editRole = { j -> editingRole = i to j },
                                addRole = {
                                    teams = TeamPlanEdit.update(teams, i) { TeamPlanEdit.addRole(it) }
                                    editingRole = i to teams[i].roles.lastIndex
                                },
                                editRoutine = { k -> editingRoutine = i to k },
                            )
                        }
                    }
                    if (teams.isEmpty()) {
                        Panel { Text("남은 팀이 없어요. 만들지 않으려면 아래 '만들지 않기'를 누르세요.", style = AxType.subheadline, color = Theme.ax.secondary) }
                    }
                    if (editable) {
                        val problem = TeamPlanEdit.problem(teams)
                        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            (error ?: problem)?.let { Text(it, style = AxType.footnote, color = SystemColor.red, textAlign = TextAlign.Center) }
                            ActionButton("이대로 만들기", kind = ActionKind.Primary, enabled = !busy && problem == null && teams.isNotEmpty()) {
                                run {
                                    if (!saveIfChanged()) return@run
                                    val client = model.client ?: return@run
                                    try {
                                        val result = client.viewAction(TeamPlanApplied.serializer(), "team-plan", "apply", JsonObject(emptyMap()))
                                        applied = TeamPlanEdit.appliedWords(result)
                                        model.reloadBots()
                                        load()
                                    } catch (e: Exception) { error = e.friendly }
                                }
                            }
                            if (changed) {
                                ActionButton("고친 것 저장", enabled = !busy && problem == null) {
                                    run { if (saveIfChanged()) model.toast = "고친 것을 저장했어요. 만들 때 이대로 만들어요." }
                                }
                            }
                            ActionButton("만들지 않기", kind = ActionKind.Danger, enabled = !busy) { declining = true }
                            Text(
                                if (changed) "아직 저장하지 않은 바뀐 것이 있어요. '이대로 만들기'를 누르면 저장하고 만들어요." else "만든 뒤에는 봇 목록과 루틴에서 고쳐요.",
                                style = AxType.caption, color = Theme.ax.secondary, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    editingTeam?.let { i ->
        teams.getOrNull(i)?.let { team ->
            TeamEditSheet(team, teams.map { it.company }.distinct(), onDismiss = { editingTeam = null }) { name, purpose, company ->
                teams = TeamPlanEdit.update(teams, i) { it.copy(name = name.trim(), purpose = purpose.trim(), company = company.trim()) }
                editingTeam = null
            }
        }
    }
    editingRole?.let { (i, j) ->
        teams.getOrNull(i)?.roles?.getOrNull(j)?.let { role ->
            RoleEditSheet(
                role,
                onDismiss = { editingRole = null },
                onRemove = { teams = TeamPlanEdit.update(teams, i) { t -> t.copy(roles = t.roles.filterIndexed { k, _ -> k != j }) }; editingRole = null },
            ) { title, does ->
                teams = TeamPlanEdit.update(teams, i) { t -> t.copy(roles = t.roles.mapIndexed { k, r -> if (k == j) r.copy(title = title.trim(), does = does.trim()) else r }) }
                editingRole = null
            }
        }
    }
    editingRoutine?.let { (i, k) ->
        teams.getOrNull(i)?.routines?.getOrNull(k)?.let { routine ->
            RoutineEditSheet(
                routine,
                onDismiss = { editingRoutine = null },
                onRemove = { teams = TeamPlanEdit.update(teams, i) { t -> t.copy(routines = t.routines.filterIndexed { n, _ -> n != k }) }; editingRoutine = null },
            ) { changedRoutine ->
                teams = TeamPlanEdit.update(teams, i) { t -> t.copy(routines = t.routines.mapIndexed { n, r -> if (n == k) changedRoutine else r }) }
                editingRoutine = null
            }
        }
    }
    removingTeam?.let { i ->
        val team = teams.getOrNull(i)
        if (team != null) MoreActionSheet(
            title = "'${team.name}' 팀을 뺄까요?",
            message = "이 팀과 직원·루틴은 만들지 않아요. 저장하거나 만들기 전까지는 새로 고치면 돌아와요.",
            actions = listOf(MoreAlertButton("팀 빼기", MoreAlertButton.Role.Destructive) { teams = TeamPlanEdit.removeTeam(teams, i) }),
            onDismiss = { removingTeam = null },
        )
    }
    if (declining) MoreActionSheet(
        title = "이 팀 구조를 만들지 않을까요?",
        message = "제안서를 버려요. 팀과 루틴은 만들지 않아요. 나중에 필요하면 봇 목록에서 팀을 하나씩 만들 수 있어요.",
        actions = listOf(MoreAlertButton("만들지 않기", MoreAlertButton.Role.Destructive) {
            run {
                val client = model.client ?: return@run
                try {
                    client.viewAction("team-plan", "decline", JsonObject(emptyMap()))
                    model.toast = "팀 구조를 만들지 않기로 했어요."
                    load()
                } catch (e: Exception) { error = e.friendly }
            }
        }),
        onDismiss = { declining = false },
    )
    applied?.let { words ->
        MoreAlert(title = "팀을 만들었어요", message = "$words 봇 목록에서 볼 수 있어요.", buttons = listOf(MoreAlertButton("확인") {}), onDismiss = { applied = null })
    }
}

@Composable
private fun StatusPanel(plan: TeamPlanView.Plan, teams: List<TeamPlanView.Team>) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = when (plan.status) { "applied" -> "만들었어요"; "declined" -> "만들지 않기로 했어요"; else -> "일 지도대로 이렇게 팀을 만들까요?" }
            Text(title, style = AxType.headline, color = Theme.ax.label)
            val companies = teams.map { it.company }.distinct().size
            val routines = teams.sumOf { it.routines.size }
            Text("회사 ${companies}곳 · 팀 ${teams.size}개 · 직원 ${teams.sumOf { it.roles.size }}명" + if (routines > 0) " · 루틴 ${routines}개" else "", style = AxType.footnote, color = Theme.ax.secondary)
            val note = when (plan.status) {
                "applied" -> (plan.appliedAt?.let { "${Format.relative(it)}에 만들었어요. " } ?: "") + "이제 봇 목록과 루틴에서 고쳐요."
                "declined" -> "이 제안서로는 팀을 만들지 않아요."
                else -> "만들기 전에 팀을 빼거나 이름·역할·루틴을 고칠 수 있어요. 개발팀에는 검수 봇이 붙어요."
            }
            Text(note, style = AxType.caption, color = Theme.ax.tertiary)
        }
    }
}

@Composable
private fun TeamPanel(
    team: TeamPlanView.Team,
    editable: Boolean,
    editTeam: () -> Unit,
    removeTeam: () -> Unit,
    editRole: (Int) -> Unit,
    addRole: () -> Unit,
    editRoutine: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(team.name, style = AxType.headline, color = Theme.ax.label)
                        if (team.isDev) {
                            Text(
                                "개발 · 검수 포함", style = AxType.caption, color = Theme.ax.accent,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Theme.ax.accent.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (team.purpose.isNotBlank()) Text(team.purpose, style = AxType.subheadline, color = Theme.ax.secondary)
                }
                if (editable) Box {
                    Icon(
                        Icons.Rounded.MoreHoriz, "${team.name} 팀 메뉴",
                        Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { menu = true }.padding(4.dp), tint = Theme.ax.accent,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("이름·목적·회사 고치기") }, onClick = { menu = false; editTeam() })
                        DropdownMenuItem(text = { Text("팀 빼기", color = SystemColor.red) }, onClick = { menu = false; removeTeam() })
                    }
                }
            }

            Column {
                Text("직원 ${team.roles.size}명" + if (team.isDev) " + 검수" else "", style = AxType.footnoteBold, color = Theme.ax.secondary)
                team.roles.forEachIndexed { j, role ->
                    if (j > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = editable) { editRole(j) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(role.title, style = AxType.body, color = Theme.ax.label)
                            if (role.does.isNotBlank()) Text(role.does, style = AxType.footnote, color = Theme.ax.secondary)
                        }
                        if (editable) Chevron()
                    }
                }
                if (team.roles.isEmpty()) Text("팀장 혼자 일해요", style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(vertical = 6.dp))
                if (editable && team.roles.size < TeamPlanEdit.MAX_ROLES) {
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = addRole).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Theme.ax.accent)
                        Text("역할 더하기", style = AxType.subheadline, color = Theme.ax.accent)
                    }
                }
            }

            if (team.routines.isNotEmpty()) {
                Column {
                    Text("루틴", style = AxType.footnoteBold, color = Theme.ax.secondary)
                    team.routines.forEachIndexed { k, routine ->
                        if (k > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = editable) { editRoutine(k) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Update, null, Modifier.size(18.dp), tint = Theme.ax.accent)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(TeamPlanEdit.scheduleText(routine.schedule), style = AxType.footnote, color = Theme.ax.accent)
                                Text(routine.title, style = AxType.body, color = Theme.ax.label)
                                Text(routine.instruction, style = AxType.footnote, color = Theme.ax.secondary, maxLines = 2)
                            }
                            if (editable) Chevron()
                        }
                    }
                }
            }

            if (team.tasks.isNotEmpty()) {
                Text("반복하는 일: " + team.tasks.joinToString(" · "), style = AxType.caption, color = Theme.ax.tertiary)
            }
        }
    }
}

/** A multi-line text box inside a form card. */
@Composable
private fun AreaField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value, onValueChange = onChange, minLines = 2, maxLines = 6,
        textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = AxType.body, color = Theme.ax.tertiary)
                inner()
            }
        },
    )
}

@Composable
private fun TeamEditSheet(team: TeamPlanView.Team, companies: List<String>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember(team) { mutableStateOf(team.name) }
    var purpose by remember(team) { mutableStateOf(team.purpose) }
    var company by remember(team) { mutableStateOf(team.company) }
    val valid = name.trim().length in 1..TeamPlanEdit.TEAM_NAME_MAX && company.trim().length in 1..TeamPlanEdit.COMPANY_MAX
    AxSheet(onDismiss) {
        SheetBar("팀 고치기", leading = { BarButton("취소") { onDismiss() } }, trailing = { BarButton("완료", bold = true, enabled = valid) { onSave(name, purpose, company) } })
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MoreFormSection(header = "팀 이름") { MoreField(name, { name = it.take(TeamPlanEdit.TEAM_NAME_MAX) }, "팀 이름") }
            MoreFormSection(header = "목적", footer = "팀장이 이 팀이 무슨 일을 하는지 알 수 있게 짧게 써 주세요.") {
                AreaField(purpose, { purpose = it.take(TeamPlanEdit.PURPOSE_MAX) }, "예: 로고·가이드 개편")
            }
            MoreFormSection(header = "회사", footer = "다른 회사로 옮기려면 그 회사를 누르거나 새 이름을 쓰세요.") {
                MoreField(company, { company = it.take(TeamPlanEdit.COMPANY_MAX) }, "회사 이름")
                companies.filter { it != company }.forEach { other ->
                    Hairline()
                    Row(
                        Modifier.fillMaxWidth().clickable { company = other }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = Theme.ax.accent)
                        Text(other, style = AxType.body, color = Theme.ax.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleEditSheet(role: TeamPlanView.Role, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(role) { mutableStateOf(role.title) }
    var does by remember(role) { mutableStateOf(role.does) }
    val valid = title.trim().length in 1..TeamPlanEdit.ROLE_TITLE_MAX
    AxSheet(onDismiss) {
        SheetBar("역할 고치기", leading = { BarButton("취소") { onDismiss() } }, trailing = { BarButton("완료", bold = true, enabled = valid) { onSave(title, does) } })
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MoreFormSection(header = "역할 이름", footer = "${title.trim().length}/${TeamPlanEdit.ROLE_TITLE_MAX}자 · 이 역할이 직원 봇 하나가 돼요.") {
                MoreField(title, { title = it.take(TeamPlanEdit.ROLE_TITLE_MAX) }, "예: 레퍼런스 조사")
            }
            MoreFormSection(header = "하는 일") { AreaField(does, { does = it.take(TeamPlanEdit.ROLE_DOES_MAX) }, "예: 비슷한 사례를 모아요") }
            MoreFormSection {
                Box(Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(vertical = 12.dp)) { Text("역할 빼기", style = AxType.body, color = SystemColor.red) }
            }
        }
    }
}

@Composable
private fun RoutineEditSheet(routine: TeamPlanView.Routine, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (TeamPlanView.Routine) -> Unit) {
    var title by remember(routine) { mutableStateOf(routine.title) }
    var instruction by remember(routine) { mutableStateOf(routine.instruction) }
    val kinds = listOf("daily", "weekly", "every")
    var kind by remember(routine) { mutableStateOf(routine.schedule.kind.takeIf { it in kinds } ?: "daily") }
    var day by remember(routine) { mutableStateOf(routine.schedule.day ?: "mon") }
    var at by remember(routine) { mutableStateOf(routine.schedule.at ?: "09:00") }
    var hours by remember(routine) { mutableStateOf(routine.schedule.hours ?: 3) }
    val schedule = when (kind) {
        "every" -> TeamPlanView.Schedule("every", hours = hours)
        "weekly" -> TeamPlanView.Schedule("weekly", day = day, at = at)
        else -> TeamPlanView.Schedule("daily", at = at)
    }
    val valid = title.trim().length in 1..TeamPlanEdit.ROUTINE_TITLE_MAX && instruction.isNotBlank() && (kind == "every" || TeamPlanEdit.validTime(at))
    AxSheet(onDismiss) {
        SheetBar(
            "루틴 고치기",
            leading = { BarButton("취소") { onDismiss() } },
            trailing = { BarButton("완료", bold = true, enabled = valid) { onSave(routine.copy(title = title.trim(), instruction = instruction.trim(), schedule = schedule)) } },
        )
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MoreFormSection(header = "제목") { MoreField(title, { title = it.take(TeamPlanEdit.ROUTINE_TITLE_MAX) }, "예: 시안 진행 점검") }
            MoreFormSection(header = "할 일", footer = "그때마다 팀장이 혼자 할 수 있게 써 주세요. 준비·정리·보고만 하고, 보내거나 결제하거나 지우지는 않아요.") {
                AreaField(instruction, { instruction = it.take(TeamPlanEdit.INSTRUCTION_MAX) }, "예: 이번 주 시안 진행 상황을 정리해 알려 줘")
            }
            MoreFormSection(header = "언제", footer = TeamPlanEdit.scheduleText(schedule)) {
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segmented(listOf("매일", "매주", "몇 시간마다"), kinds.indexOf(kind)) { kind = kinds[it] }
                    if (kind == "weekly") Segmented(TeamPlanEdit.DAYS.map { TeamPlanEdit.dayWord(it) }, TeamPlanEdit.DAYS.indexOf(day).coerceAtLeast(0)) { day = TeamPlanEdit.DAYS[it] }
                    if (kind == "every") Stepper("${hours}시간마다", hours, 1..24) { hours = it }
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시각", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                        Box(Modifier.width(84.dp).clip(RoundedCornerShape(8.dp)).background(Theme.ax.fill).padding(horizontal = 12.dp)) {
                            MoreField(
                                at, { at = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, "09:30",
                                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), style = AxType.body.copy(textAlign = TextAlign.Center),
                            )
                        }
                    }
                    if (kind != "every" && !TeamPlanEdit.validTime(at)) Text("시각은 09:30처럼 24시간으로 써 주세요.", style = AxType.footnote, color = SystemColor.red)
                }
            }
            MoreFormSection {
                Box(Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(vertical = 12.dp)) { Text("루틴 빼기", style = AxType.body, color = SystemColor.red) }
            }
        }
    }
}

/** The bots-tab card: "팀 구조 제안", highlighted while it waits for the owner. */
@Composable
fun TeamPlanCard(plan: TeamPlanView.Plan, onOpen: () -> Unit) {
    val proposed = plan.proposed
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (proposed) Theme.ax.accent.copy(alpha = 0.10f) else Theme.ax.page)
            .then(if (proposed) Modifier.border(1.dp, Theme.ax.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onOpen).padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "팀 구조 제안, ${TeamPlanEdit.cardLine(plan)}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.AccountTree, null, Modifier.size(26.dp), tint = if (proposed) Theme.ax.accent else Theme.ax.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("팀 구조 제안", style = AxType.headline, color = Theme.ax.label)
            Text(TeamPlanEdit.cardLine(plan), style = AxType.footnote, color = Theme.ax.secondary)
        }
        if (proposed) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Theme.ax.accent))
        Chevron()
    }
}
