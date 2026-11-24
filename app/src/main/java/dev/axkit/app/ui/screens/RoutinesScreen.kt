package dev.axkit.app.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.Format
import dev.axkit.core.RoutinesView
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 루틴 (§17): things the assistant or a bot does on a schedule. New ones are made by asking in the chat. Port of RoutinesScreen.swift. */
@Composable
fun RoutinesScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<RoutinesView>>(Loadable.Loading) }
    var removing by remember { mutableStateOf<RoutinesView.Routine?>(null) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(RoutinesView.serializer(), "routines") }
    }
    LaunchedEffect(Unit) { load() }

    fun set(r: RoutinesView.Routine, enabled: Boolean) = scope.launch {
        try {
            model.client?.viewAction("routines", "toggle", buildJsonObject { put("id", JsonPrimitive(r.id)); put("enabled", JsonPrimitive(enabled)) })
        } catch (e: Exception) { model.toast = e.friendly }
        load()
    }

    Screen("루틴", large = true) {
        LoadableView(state, retry = { load() }) { data ->
            MoreRefresh(onRefresh = { load() }) {
                Page {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("새 루틴은 비서에게 말로 부탁하세요. 예: \"매일 아침 9시에 오늘 일정 알려 줘\"", style = AxType.subheadline, color = Theme.ax.secondary)
                            ActionButton("비서에게 말하러 가기", kind = ActionKind.Primary) { model.showRoom("") }
                        }
                    }
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("루틴 ${data.routines.size}개", data.limit?.let { "${it}개까지 만들 수 있어요" })
                            if (data.routines.isEmpty()) Text("아직 루틴이 없어요", style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.padding(vertical = 8.dp))
                            CardList(data.routines) { r -> RoutineRow(r, toggle = { set(r, it) }, remove = { removing = r }) }
                        }
                    }
                    val follow = data.followUps
                    if (!follow.isNullOrEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader("봇이 나중에 다시 볼 것")
                                CardList(follow) { f ->
                                    CardRow(f.what, listOfNotNull(f.bot?.name, f.dueAt?.let { Format.deadline(it) }).joinToString(" · "))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    removing?.let { r ->
        MoreActionSheet(
            title = "'${r.title}' 루틴을 지울까요?",
            message = "다시는 돌지 않아요. 잠깐 쉬게 하려면 지우지 말고 스위치를 꺼 주세요.",
            actions = listOf(MoreAlertButton("지우기", MoreAlertButton.Role.Destructive) {
                scope.launch {
                    try {
                        model.client?.viewAction("routines", "remove", buildJsonObject { put("id", JsonPrimitive(r.id)) })
                        model.toast = "루틴을 지웠어요"
                    } catch (e: Exception) { model.toast = e.friendly }
                    load()
                }
            }),
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun RoutineRow(routine: RoutinesView.Routine, toggle: (Boolean) -> Unit, remove: () -> Unit) {
    var on by remember(routine.id, routine.enabled) { mutableStateOf(routine.enabled) }
    var menu by remember { mutableStateOf(false) }
    val parts = mutableListOf(routine.scheduleLabel ?: "")
    routine.bot?.let { parts.add(it.name) }
    if (!routine.enabled) parts.add("꺼짐")
    var failed = false
    routine.lastRun?.let { at ->
        if (routine.lastOk == false) { parts.add("${Format.relative(at)} 실패"); failed = true } else parts.add("${Format.relative(at)} 했어요")
    }
    val line = parts.filter { it.isNotEmpty() }.joinToString(" · ")
    // iOS: long-press menu / swipe to 지우기; Android: long-press menu.
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(vertical = 10.dp)
            .semantics { customActions = listOf(CustomAccessibilityAction("루틴 지우기") { remove(); true }) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(routine.title, style = AxType.body, color = Theme.ax.label)
            Text(line, style = AxType.footnote, color = if (failed) SystemColor.red else Theme.ax.secondary)
            val result = routine.lastResult
            if (!result.isNullOrEmpty()) Text(result, style = AxType.caption, color = Theme.ax.secondary, maxLines = 2)
        }
        MoreAccentSwitch(on) { on = it; toggle(it) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("루틴 지우기", color = SystemColor.red) }, onClick = { menu = false; remove() })
        }
    }
}
