package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
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
import dev.axkit.core.ProjectsDraft
import dev.axkit.core.ProjectsSaveResult
import dev.axkit.core.ProjectsView
import dev.axkit.core.ProjectsWords
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 프로젝트 가져오기 (§23): which projects the first look reads, by company. Tick them, fix names, move one to another
 * company or rename a company, then save (and read now, or at the next night's check). Port of ProjectsScreen.swift.
 */
@Composable
fun ProjectsScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<ProjectsView>>(Loadable.Loading) }
    var picks by remember { mutableStateOf(ProjectsDraft(emptyList())) }
    var saved by remember { mutableStateOf(ProjectsDraft(emptyList())) }
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectsDraft.Row?>(null) }
    var renamingCompany by remember { mutableStateOf<String?>(null) }
    val changed = picks != saved

    fun show(data: ProjectsView) { state = Loadable.Loaded(data); picks = ProjectsDraft.from(data); saved = picks }

    suspend fun load() {
        val client = model.client ?: return
        try { show(client.view(ProjectsView.serializer(), "projects")) } catch (e: Exception) { state = Loadable.Failed(e.friendly) }
    }

    suspend fun refresh() {
        val client = model.client ?: return
        if (changed) model.toast = "새로 고치면 저장하지 않은 바뀐 것은 사라져요."
        try { show(client.viewAction(ProjectsView.serializer(), "projects", "refresh", JsonObject(emptyMap()))) } catch (e: Exception) { model.toast = e.friendly }
    }

    fun save(rescan: Boolean) = scope.launch {
        val client = model.client ?: return@launch
        saving = true
        try {
            val answer = client.viewAction(ProjectsSaveResult.serializer(), "projects", "save", picks.body(rescan))
            model.toast = ProjectsWords.saved(answer, picks.pickedCount)
            load()
        } catch (e: Exception) {
            model.toast = e.friendly
        }
        saving = false
    }

    LaunchedEffect(Unit) { load() }

    Screen("프로젝트 가져오기") {
        LoadableView(state, retry = { load() }) { data ->
            MoreRefresh(onRefresh = { refresh() }) {
                Page {
                    ScanPanel(data.scan, picks.pickedCount)
                    if (picks.rows.isEmpty()) {
                        Panel {
                            Text(
                                "아직 찾은 프로젝트가 없어요. 맥의 설정에서 고른 폴더의 git 저장소와 Claude Code·Codex 대화 기록이 있는 폴더를 찾아요.",
                                style = AxType.subheadline, color = Theme.ax.secondary,
                            )
                        }
                    }
                    picks.companies.forEach { (company, rows) ->
                        CompanyPanel(
                            company, rows,
                            toggle = { picks = picks.toggle(it) },
                            renameCompany = { renamingCompany = company },
                            edit = { editing = it },
                        )
                    }
                    if (picks.rows.isNotEmpty()) {
                        val problem = picks.problem
                        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (problem != null) Text(problem, style = AxType.footnote, color = SystemColor.red)
                            ActionButton("저장하고 바로 읽기", kind = ActionKind.Primary, enabled = !saving && problem == null) { save(rescan = true) }
                            ActionButton("저장만", enabled = !saving && problem == null) { save(rescan = false) }
                            Text(
                                if (changed) "아직 저장하지 않은 바뀐 것이 있어요. '저장만'이면 다음 날 새벽 정기 확인 때 읽어요." else "'저장만'이면 다음 날 새벽 정기 확인 때 읽어요.",
                                style = AxType.caption, color = Theme.ax.secondary, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { row ->
        ProjectEditSheet(row, picks.companyNames, onDismiss = { editing = null }) { name, company ->
            picks = picks.rename(row.project.path, name).move(row.project.path, company)
            editing = null
        }
    }
    renamingCompany?.let { old ->
        var text by remember(old) { mutableStateOf(old) }
        MoreAlert(
            title = "회사 이름 바꾸기",
            message = "이 회사의 프로젝트가 모두 새 이름 아래로 가요. 저장해야 맥에 반영돼요.",
            buttons = listOf(
                MoreAlertButton("취소", MoreAlertButton.Role.Cancel) {},
                MoreAlertButton("바꾸기") { picks = picks.renameCompany(old, text) },
            ),
            onDismiss = { renamingCompany = null },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(7.dp)).background(Theme.ax.page).padding(horizontal = 8.dp)) {
                MoreField(text, { text = it }, "회사 이름", style = AxType.subheadline)
            }
        }
    }
}

@Composable
private fun ScanPanel(scan: ProjectsView.Scan?, picked: Int) {
    val running = scan?.status == "running"
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) CircularProgressIndicator(Modifier.size(16.dp), color = Theme.ax.secondary, strokeWidth = 2.dp)
                Text(ProjectsWords.status(scan), style = AxType.headline, color = Theme.ax.label, modifier = Modifier.weight(1f))
                Text("${picked}개 고름", style = AxType.footnote, color = Theme.ax.secondary)
            }
            ProjectsWords.counts(scan?.counts)?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
            if (!running) scan?.finishedAt?.let { Text("마지막으로 다 읽은 때: ${Format.relative(it)}", style = AxType.footnote, color = Theme.ax.secondary) }
            Text(
                "처음 읽을 때는 꼼꼼히 다 읽어서 몇십 분에서 몇 시간 걸릴 수 있어요. 진행 소식은 대화창으로 알려 드려요.",
                style = AxType.caption, color = Theme.ax.tertiary,
            )
        }
    }
}

@Composable
private fun CompanyPanel(
    company: String,
    rows: List<ProjectsDraft.Row>,
    toggle: (String) -> Unit,
    renameCompany: () -> Unit,
    edit: (ProjectsDraft.Row) -> Unit,
) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardHeader(company, "${rows.count { it.picked }} / ${rows.size}개 고름") }
                Text(
                    "이름 바꾸기", style = AxType.footnote, color = Theme.ax.accent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = renameCompany).padding(6.dp)
                        .semantics { contentDescription = "$company 회사 이름 바꾸기" },
                )
            }
            CardList(rows) { r -> ProjectRow(r, toggle = { toggle(r.project.path) }, edit = { edit(r) }) }
        }
    }
}

@Composable
private fun ProjectRow(row: ProjectsDraft.Row, toggle: () -> Unit, edit: () -> Unit) {
    val p = row.project
    val missing = p.lastActive == null
    val canToggle = !(missing && !row.picked)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (row.picked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            if (row.picked) "${row.name} 빼기" else "${row.name} 고르기",
            Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable(enabled = canToggle, onClick = toggle),
            tint = if (row.picked) Theme.ax.accent else Theme.ax.secondary,
        )
        Column(Modifier.weight(1f).clickable(enabled = canToggle, onClick = toggle), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.name, style = AxType.body, color = if (missing) Theme.ax.secondary else Theme.ax.label)
            Text(
                p.display ?: p.path, style = AxType.caption.copy(fontFamily = FontFamily.Monospace), color = Theme.ax.secondary,
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
            )
            Text(ProjectsWords.detail(p), style = AxType.caption, color = if (missing) SystemColor.orange else Theme.ax.secondary)
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Theme.ax.accent.copy(alpha = 0.12f)).clickable(onClick = edit)
                .padding(horizontal = 10.dp, vertical = 6.dp).semantics { contentDescription = "${row.name} 이름·회사 고치기" },
        ) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = Theme.ax.accent) }
    }
}

/** Fix one project's name, or move it to another company (iOS ProjectEditSheet). */
@Composable
private fun ProjectEditSheet(row: ProjectsDraft.Row, companies: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(row) { mutableStateOf(row.name) }
    var company by remember(row) { mutableStateOf(row.company) }
    val n = name.trim(); val c = company.trim()
    val valid = n.isNotEmpty() && c.isNotEmpty() && n.length <= ProjectsDraft.NAME_MAX && c.length <= ProjectsDraft.NAME_MAX
    AxSheet(onDismiss) {
        SheetBar(
            "프로젝트 고치기",
            leading = { BarButton("취소") { onDismiss() } },
            trailing = { BarButton("완료", bold = true, enabled = valid) { onSave(name, company) } },
        )
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MoreFormSection(footer = row.project.display ?: row.project.path) {
                MoreField(name, { name = it }, "프로젝트 이름")
            }
            MoreFormSection(header = "회사", footer = "다른 회사로 옮기려면 그 회사를 누르거나 새 이름을 쓰세요. 저장해야 맥에 반영돼요.") {
                MoreField(company, { company = it }, "회사 이름")
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
