package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.Chip
import dev.axkit.app.ui.design.InputRow
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.loaded
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.FirstLookRevised
import dev.axkit.core.FirstLookView
import dev.axkit.core.FirstLookWords
import dev.axkit.core.TeamPlanApplied
import dev.axkit.core.TeamPlanEdit
import dev.axkit.core.friendly
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 더보기 › 처음 알아보기 (§27, feature `first-look`): the bar while it reads (polled every second while running), then
 * "당신은 이런 사람이군요" and the proposed companies → projects → teams, fixed in plain words (`revise`) and built (`apply`).
 */
@Composable
fun FirstLookScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<FirstLookView>>(Loadable.Loading) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val client = model.client ?: return
        try { state = Loadable.Loaded(client.view(FirstLookView.serializer(), "first-look")) }
        catch (e: Exception) { if (state !is Loadable.Loaded) state = Loadable.Failed(e.friendly) }
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch { try { block() } catch (e: Exception) { error = e.friendly } finally { busy = false } }
    }

    LaunchedEffect(Unit) { load() }
    val running = state.loaded?.running == true
    /** After 'start' the Mac may not say `running` at once: ask a few more times anyway. */
    var boost by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Light enough to ask every second (§27.1); only while it is reading and the screen is on view.
    LaunchedEffect(running, boost > 0) {
        if (!running && boost <= 0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { delay(1000); load(); if (boost > 0) boost-- }
        }
    }

    Screen("처음 알아보기") {
        LoadableView(state, retry = { load() }) { view ->
            MoreRefresh(onRefresh = { load() }) {
                Page {
                    ProgressPanel(view)
                    FirstLookWords.startLabel(view)?.let { label ->
                        ActionButton(label, kind = ActionKind.Primary, enabled = !busy) {
                            run {
                                model.client?.viewAction("first-look", "start", JsonObject(emptyMap()))
                                load()
                                boost = 5
                            }
                        }
                    }
                    val result = view.result
                    if (result != null) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CardHeader("당신은 이런 사람이군요")
                                Text(result.about.ifBlank { "아직 한 줄로 정리하지 못했어요" }, style = AxType.body, color = Theme.ax.label)
                            }
                        }
                        Text("이런 조직 분류가 필요해 보여요", style = AxType.title3, color = Theme.ax.label, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                        if (result.companies.isEmpty()) {
                            Panel { Text("나눌 회사·프로젝트를 찾지 못했어요", style = AxType.subheadline, color = Theme.ax.secondary) }
                        }
                        for (company in result.companies) CompanyTree(company)
                        val plan = result.plan
                        when {
                            plan == null -> {}
                            plan.proposed -> {
                                Panel {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CardHeader("말로 고치기", "예: 에이컴퍼니랑 비컴퍼니는 따로야. 쇼핑몰은 비컴퍼니 아래")
                                        InputRow(
                                            text = said, onText = { said = it.take(500) }, placeholder = "무엇을 바꿀지 말해 주세요",
                                            busy = busy && said.isNotBlank(), enabled = said.isNotBlank() && !busy,
                                        ) {
                                            run {
                                                val client = model.client ?: return@run
                                                val body = buildJsonObject { put("text", JsonPrimitive(said.trim())) }
                                                val r = client.viewAction(FirstLookRevised.serializer(), "first-look", "revise", body)
                                                reply = r.text.ifBlank { null }
                                                if (r.changed.isNotEmpty()) said = ""
                                                load()
                                            }
                                        }
                                        reply?.let {
                                            Text(
                                                it, style = AxType.subheadline, color = Theme.ax.label,
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Theme.ax.fill).padding(10.dp),
                                            )
                                        }
                                    }
                                }
                                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    error?.let { Text(it, style = AxType.footnote, color = SystemColor.red, textAlign = TextAlign.Center) }
                                    ActionButton("이대로 만들기", kind = ActionKind.Primary, enabled = !busy && result.companies.isNotEmpty()) {
                                        run {
                                            val client = model.client ?: return@run
                                            val made = client.viewAction(TeamPlanApplied.serializer(), "first-look", "apply", JsonObject(emptyMap()))
                                            applied = TeamPlanEdit.appliedWords(made)
                                            model.reloadBots()
                                            load()
                                        }
                                    }
                                    if (model.capabilities.has("team-plan")) {
                                        ActionButton("팀·역할·루틴 하나씩 고치기", enabled = !busy) { nav.push("team-plan") { TeamPlanScreen() } }
                                    }
                                    Text("만든 뒤에는 봇 목록과 루틴에서 고쳐요.", style = AxType.caption, color = Theme.ax.secondary, textAlign = TextAlign.Center)
                                }
                            }
                            plan.status == "applied" -> Panel { Text("이 구조대로 팀을 만들었어요. 봇 목록에서 볼 수 있어요.", style = AxType.subheadline, color = Theme.ax.secondary) }
                            else -> Panel { Text("이 구조로는 만들지 않기로 했어요.", style = AxType.subheadline, color = Theme.ax.secondary) }
                        }
                    }
                    if (result == null || result.plan?.proposed != true) {
                        error?.let { Text(it, style = AxType.footnote, color = SystemColor.red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
    applied?.let { words ->
        MoreAlert(title = "팀을 만들었어요", message = "$words 봇 목록에서 볼 수 있어요.", buttons = listOf(MoreAlertButton("확인") {}), onDismiss = { applied = null })
    }
}

/** What it is reading, the bar with percent and time, then the counts (the terminal's three lines). */
@Composable
private fun ProgressPanel(view: FirstLookView) {
    val percent = Math.round(view.percent.coerceIn(0.0, 100.0)).toInt()
    // Redrawn by the 1-second poll while it runs, so the time moves too.
    val time = FirstLookWords.elapsedText(FirstLookWords.elapsed(view))
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader("처음 알아보기", FirstLookWords.head(view))
            LinearProgressIndicator(
                progress = { FirstLookWords.fraction(view) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${percent}% 읽음" },
                color = if (view.status == "failed") SystemColor.red else Theme.ax.accent, trackColor = Theme.ax.fill, drawStopIndicator = {},
            )
            Text(
                if (view.startedAt != null) "$percent% · $time" else "$percent%",
                style = AxType.subheadlineBold.copy(fontFeatureSettings = "tnum"), color = Theme.ax.label,
            )
            Text(FirstLookWords.counts(view), style = AxType.footnote, color = Theme.ax.secondary)
            when (view.status) {
                "running" -> Text("화면을 닫아도 맥에서 계속 읽어요. 처음엔 몇십 분에서 몇 시간 걸릴 수 있어요.", style = AxType.caption, color = Theme.ax.secondary)
                "paused" -> Text("멈춰 있어요. '알아보기 계속'을 누르면 이어서 읽어요.", style = AxType.caption, color = Theme.ax.secondary)
                "failed" -> Text("읽다가 멈췄어요${view.error?.let { " ($it)" } ?: ""}. '알아보기 계속'으로 다시 해 볼 수 있어요.", style = AxType.caption, color = SystemColor.red)
                "idle" -> Text("맥의 폴더·프로젝트·드라이브를 읽고 어떤 일을 하는지 정리해요.", style = AxType.caption, color = Theme.ax.secondary)
            }
        }
    }
}

/** ■ company (relation) / · project — team: roles / ↻ routine. */
@Composable
private fun CompanyTree(company: FirstLookView.Company) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(company.name, style = AxType.headline, color = Theme.ax.label, modifier = Modifier.weight(1f, fill = false))
                Chip(FirstLookWords.relation(company.relation))
            }
            for (project in company.projects) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("·", style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(project.name, style = AxType.bodyBold, color = Theme.ax.label)
                        Text(FirstLookWords.team(project), style = AxType.subheadline, color = Theme.ax.secondary)
                        if (project.purpose.isNotBlank()) Text(project.purpose, style = AxType.footnote, color = Theme.ax.secondary)
                        for (r in project.routines) Text("↻ ${r.whenText} ${r.title}".trim(), style = AxType.footnote, color = Theme.ax.accent)
                    }
                }
            }
            if (company.projects.isEmpty()) Text("아직 프로젝트가 없어요", style = AxType.footnote, color = Theme.ax.secondary)
        }
    }
}
