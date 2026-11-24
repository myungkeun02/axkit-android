package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.Chip
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.WorkPanel
import dev.axkit.core.WorkView
import dev.axkit.core.WorkWords
import kotlinx.coroutines.delay

/**
 * §31 작업 시트: over the chat box, one folded line "하는 중 N개 · 맨 위 일" (hidden when nothing runs); a tap opens a
 * sheet with what runs on top and the last few that ended, sub-jobs indented under the job that handed them out.
 * While something runs `views/work` is read every 15 seconds (only while the screen is on view); otherwise when a notice comes.
 */
@Composable
fun WorkStrip() {
    val model = LocalModel.current
    if (!model.capabilities.hasView("work")) return
    var view by remember { mutableStateOf<WorkView?>(null) }
    var open by remember { mutableStateOf(false) }

    suspend fun load() {
        val client = model.client ?: return
        runCatching { client.view(WorkView.serializer(), "work") }.onSuccess { view = it }
    }
    LaunchedEffect(model.noticesSeen) { load() }
    val running = view?.active?.any { it.isActive } == true
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { while (true) { delay(15_000); load() } }
    }

    val line = WorkPanel.strip(view)
    if (line != null) {
        Row(
            Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Theme.ax.card).clickable { open = true }.padding(horizontal = 14.dp, vertical = 9.dp)
                .semantics { contentDescription = "$line, 누르면 맡긴 일 보기" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Theme.ax.accent)
            Text(line, style = AxType.footnote, color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(18.dp), tint = Theme.ax.tertiary)
        }
    }
    val shown = view
    if (open && shown != null) WorkSheet(shown, onClose = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkSheet(view: WorkView, onClose: () -> Unit) {
    val model = LocalModel.current
    val layout = WorkPanel.layout(view)
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, containerColor = Theme.ax.page,
        dragHandle = { Box(Modifier.padding(top = 6.dp).size(width = 36.dp, height = 5.dp).background(Theme.ax.tertiary, CircleShape)) },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(view.title, style = AxType.title3, color = Theme.ax.label, modifier = Modifier.padding(vertical = 10.dp))
            Section("하는 중", if (layout.runningCount > 0) "${layout.runningCount}개" else null)
            if (layout.active.isEmpty()) Text("지금은 하는 일이 없어요.", style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(vertical = 8.dp))
            layout.active.forEachIndexed { i, row -> if (i > 0) Hairline(); SheetRow(row) { open(model, row.item, onClose) } }
            if (layout.recent.isNotEmpty()) {
                Section("최근에 끝난 일", null, top = 18)
                layout.recent.forEachIndexed { i, row -> if (i > 0) Hairline(); SheetRow(row) { open(model, row.item, onClose) } }
            }
        }
    }
}

private fun open(model: dev.axkit.app.AppModel, item: WorkView.Item, onClose: () -> Unit) {
    val room = WorkPanel.room(item) { id -> model.bots?.bot(id)?.channel } ?: return
    onClose()
    model.showRoom(room)
}

@Composable
private fun Section(title: String, detail: String?, top: Int = 0) {
    Row(Modifier.padding(top = top.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = AxType.subheadlineBold, color = Theme.ax.secondary)
        detail?.let { Text(it, style = AxType.subheadline, color = Theme.ax.tertiary) }
    }
}

@Composable
private fun SheetRow(row: WorkPanel.Row, onOpen: () -> Unit) {
    val model = LocalModel.current
    val item = row.item
    val opens = WorkPanel.room(item) { id -> model.bots?.bot(id)?.channel } != null
    Row(
        Modifier.fillMaxWidth().then(if (opens) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(start = (row.depth * 18).dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (row.depth > 0) Box(Modifier.padding(top = 4.dp).width(2.dp).height(16.dp).background(Theme.ax.separator))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title.ifBlank { item.id }, style = if (row.depth == 0) AxType.bodyBold else AxType.body, color = Theme.ax.label,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Chip(WorkWords.status(item.status), color = when {
                    item.status == "failed" || item.status == "interrupted" -> SystemColor.red
                    item.status == "waiting" -> SystemColor.orange
                    item.status == "done" -> SystemColor.green
                    else -> null
                })
            }
            val line = WorkWords.line(item)
            if (line.isNotEmpty()) Text(line, style = AxType.footnote, color = Theme.ax.secondary)
            // Sub-jobs can be tapped too; only the top row says where it goes, so the list stays quiet.
            if (opens && row.depth == 0) {
                val bot = item.origin?.address?.removePrefix("bot:")?.let { model.bots?.bot(it) }
                Text(if (item.origin?.address == "owner") "비서 대화 열기 ›" else "${bot?.name ?: "봇"} 방 열기 ›", style = AxType.caption, color = Theme.ax.accent)
            }
        }
    }
}
