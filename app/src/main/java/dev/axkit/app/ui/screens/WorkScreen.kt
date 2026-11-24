package dev.axkit.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.Chip
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loaded
import dev.axkit.core.ChatText
import dev.axkit.core.WorkView
import dev.axkit.core.WorkWords
import dev.axkit.core.friendly
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

/**
 * 더보기 › 하고 있는 일 (§28 `views/work`): long jobs the assistant runs in the background (dev work, browser and life
 * errands) — what is going on now, then what ended lately. Read only; results also arrive as notices in the chat.
 */
@Composable
fun WorkScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<WorkView>>(Loadable.Loading) }

    suspend fun load() {
        val client = model.client ?: return
        try { state = Loadable.Loaded(client.view(WorkView.serializer(), "work")) }
        catch (e: Exception) { if (state !is Loadable.Loaded) state = Loadable.Failed(e.friendly) }
    }

    LaunchedEffect(Unit) { load() }
    val busy = state.loaded?.active?.isNotEmpty() == true
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // While something runs and the screen is on view, read again every 10 seconds so the step and minutes move.
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { while (true) { delay(10_000); load() } }
    }

    Screen(state.loaded?.title ?: "하고 있는 일") {
        LoadableView(state, retry = { load() }) { view ->
            MoreRefresh(onRefresh = { load() }) {
                Page {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("지금 하고 있는 일", if (view.active.isEmpty()) null else "${view.active.size}개")
                            if (view.active.isEmpty()) {
                                Text("지금은 하고 있는 일이 없어요. 오래 걸리는 일을 맡기면 여기에 보여요.", style = AxType.subheadline, color = Theme.ax.secondary)
                            } else CardList(view.active) { WorkRow(it) }
                        }
                    }
                    if (view.recent.isNotEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader("최근에 끝난 일")
                                CardList(view.recent) { WorkRow(it) }
                            }
                        }
                    }
                    Text(
                        "끝나면 대화창에 결과가 오고, 앱을 안 보고 있으면 알림으로도 알려요. 대화창에서 \"어떻게 됐어?\"라고 물어도 돼요.",
                        style = AxType.caption, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkRow(item: WorkView.Item) {
    val model = LocalModel.current
    // §28/§31 origin: `owner` opens the main chat, `bot:<id>` that bot's room; anything else stays put.
    val room = dev.axkit.core.WorkPanel.room(item) { id -> model.bots?.bot(id)?.channel }
    val fromApp = room != null
    Column(
        Modifier.fillMaxWidth().then(if (room != null) Modifier.clickable { model.showRoom(room) } else Modifier).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title.ifBlank { item.id }, style = AxType.bodyBold, color = Theme.ax.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Chip(WorkWords.status(item.status), color = when {
                item.status == "failed" || item.status == "interrupted" -> SystemColor.red
                item.status == "waiting" -> SystemColor.orange
                item.status == "done" -> SystemColor.green
                else -> null
            })
        }
        val line = WorkWords.line(item)
        if (line.isNotEmpty()) Text(line, style = AxType.footnote, color = Theme.ax.secondary)
        WorkWords.origin(item)?.let { Text(it, style = AxType.caption, color = if (fromApp) Theme.ax.accent else Theme.ax.secondary) }
        if (item.status == "waiting") Text("로그인·버튼 누르기·결제처럼 직접 하실 단계에서 멈췄어요. 대화창의 안내를 봐 주세요.", style = AxType.footnote, color = SystemColor.orange)
        // Plain text only: a summary may hold words read off a web page (§28.1).
        item.summary?.takeIf { it.isNotBlank() }?.let { Text(ChatText.preview(it, 300), style = AxType.subheadline, color = Theme.ax.label) }
        item.link?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AxType.caption.copy(fontFamily = FontFamily.Monospace), color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}
