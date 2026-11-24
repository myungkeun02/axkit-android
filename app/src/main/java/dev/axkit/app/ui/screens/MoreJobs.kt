package dev.axkit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.Format
import dev.axkit.core.JobsView
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 반복 작업: what the assistant runs on a schedule, with on/off switches. */
@Composable
fun JobsScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<JobsView>>(Loadable.Loading) }
    val canToggle = model.capabilities.view("jobs")?.actions?.contains("toggle") == true

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(JobsView.serializer(), "jobs") }
    }

    suspend fun set(job: JobsView.Job, enabled: Boolean) {
        val client = model.client ?: return
        try {
            client.viewAction("jobs", "toggle", buildJsonObject { put("id", JsonPrimitive(job.id)); put("enabled", JsonPrimitive(enabled)) })
            model.toast = if (enabled) "'${job.name}'을 켰어요" else "'${job.name}'을 껐어요"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { model.toast = e.friendly }
        load()
    }

    LaunchedEffect(Unit) { load() }
    Screen("반복 작업", large = true) {
        LoadableView(state, retry = { load() }) { data ->
            MoreRefresh({ load() }) {
                Page {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("새 반복 작업은 비서에게 말로 부탁하세요. 예: \"매일 아침 8시에 날씨 알려 줘\"", style = AxType.subheadline, color = Theme.ax.secondary)
                            ActionButton("비서에게 말하러 가기", kind = ActionKind.Primary) { model.showRoom("") }
                        }
                    }
                    data.groups.forEach { group ->
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader(group.label)
                                CardList(group.jobs) { job ->
                                    JobRow(job, if (canToggle && job.canToggle) { on -> set(job, on) } else null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobsView.Job, toggle: (suspend (Boolean) -> Unit)?) {
    val scope = rememberCoroutineScope()
    var on by remember(job.id, job.enabled) { mutableStateOf(job.enabled) }
    val (text, color) = jobLine(job)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(job.name, style = AxType.body, color = Theme.ax.label)
            Text(text, style = AxType.footnote, color = color)
        }
        if (toggle != null) {
            MoreAccentSwitch(on) { value -> on = value; scope.launch { toggle(value) } }
        } else {
            Text(if (job.enabled) "켜짐" else "꺼짐", style = AxType.footnote, color = Theme.ax.secondary)
        }
    }
}

/** One grey line: when it runs and how the last run went. Problems in red. */
@Composable
private fun jobLine(job: JobsView.Job): Pair<String, Color> {
    val parts = mutableListOf(job.scheduleLabel ?: "")
    if (job.running == true) parts.add("도는 중")
    val run = job.lastRun
    val at = run?.startedAt
    if (run != null && at != null) {
        if (run.ok == false) {
            return (parts + "${Format.relative(at)} 문제: ${run.error ?: ""}").joinToString(" · ") to SystemColor.red
        }
        parts.add("${Format.relative(at)} 잘 끝남")
    }
    return parts.filter { it.isNotEmpty() }.joinToString(" · ") to Theme.ax.secondary
}
