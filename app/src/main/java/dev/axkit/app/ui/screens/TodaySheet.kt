package dev.axkit.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.Tab
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SectionLabel
import dev.axkit.app.ui.design.InlineMarkdown
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.ChatText
import dev.axkit.core.Format
import dev.axkit.core.ISODates
import dev.axkit.core.TodayView
import kotlinx.coroutines.launch

/** "2026-10-19" on 2026-11-20 → ("32일 지남", true); today → ("오늘까지", false). */
fun todayDueWords(due: String, today: String): Pair<String, Boolean>? {
    val d = runCatching { java.time.LocalDate.parse(due.take(10)) }.getOrNull() ?: return null
    val t = runCatching { java.time.LocalDate.parse(today.take(10)) }.getOrNull() ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, t).toInt()
    return when {
        days > 0 -> "${days}일 지남" to true
        days == 0 -> "오늘까지" to false
        days == -1 -> "내일까지" to false
        else -> "${d.monthValue}월 ${d.dayOfMonth}일까지" to false
    }
}

/** iOS ExpandableText: a long to-do shows `limit` lines and 더 보기 / 접기. */
@Composable
private fun TodayExpandableText(text: String, limit: Int) {
    var open by remember { mutableStateOf(false) }
    val isLong = text.length > limit * 38 || text.split("\n").size > limit
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            InlineMarkdown.parse(text, Theme.ax.accent), style = AxType.body, color = Theme.ax.label,
            maxLines = if (open || !isLong) Int.MAX_VALUE else limit, overflow = TextOverflow.Ellipsis,
        )
        if (isLong) {
            Text(
                if (open) "접기" else "더 보기", style = AxType.footnoteBold, color = Theme.ax.accent,
                modifier = Modifier.clickable { open = !open },
            )
        }
    }
}

/** 홈 › 오늘: today's plans, to-dos and how many 결재 are waiting. Drawn inside an AxSheet (HomeView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySheet(onClose: () -> Unit) {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<TodayView>>(Loadable.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(TodayView.serializer(), "today") }
    }
    LaunchedEffect(Unit) { load() }

    SheetBar("오늘", trailing = { BarButton("닫기", bold = true) { onClose() } })
    LoadableView(state, retry = { load() }) { today ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val open = model.openCardCount
                if (open > 0) {
                    Panel(padding = 0.dp) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onClose(); model.tab = Tab.Approvals }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("결재 기다리는 것 ${open}개", style = AxType.body, color = Theme.ax.accent, modifier = Modifier.weight(1f))
                            Chevron()
                        }
                    }
                }
                SectionLabel("일정", Modifier.padding(top = 10.dp))
                Panel(padding = 0.dp) {
                    val events = today.events
                    if (!events.isNullOrEmpty()) {
                        CardList(events) { e ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                                Text(
                                    if (e.allDay == true) "종일" else ISODates.parse(e.start)?.let { Format.clock(it) } ?: "",
                                    style = AxType.subheadline.copy(fontFeatureSettings = "tnum"), color = Theme.ax.secondary,
                                    modifier = Modifier.width(76.dp).padding(top = 2.dp),
                                )
                                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(ChatText.preview(e.title, 80), style = AxType.body, color = Theme.ax.label)
                                    // §36: the calendar's name when the Mac has two or more.
                                    e.calendar?.takeIf { it.isNotBlank() }?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
                                }
                            }
                        }
                    } else {
                        Text(
                            if (events == null) "캘린더가 아직 연결되지 않았어요" else "남은 일정이 없어요",
                            style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                SectionLabel("할 일", Modifier.padding(top = 10.dp))
                Panel(padding = 0.dp) {
                    val todos = today.todos
                    if (!todos.isNullOrEmpty()) {
                        CardList(todos) { t ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TodayExpandableText(ChatText.clean(t.text), limit = 3)
                                val words = t.due?.let { todayDueWords(it, today.date) }
                                if (words != null) {
                                    Text(words.first, style = AxType.captionBold, color = if (words.second) SystemColor.red else Theme.ax.secondary)
                                }
                            }
                        }
                    } else {
                        Text("오늘 할 일이 없어요", style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                // §5.8: chats and mails waiting for an answer (null = neither source is on, so no section).
                val replies = today.replies
                if (replies != null) {
                    SectionLabel("답을 기다리는 것", Modifier.padding(top = 10.dp))
                    Panel(padding = 0.dp) {
                        if (replies.isNotEmpty()) {
                            CardList(replies) { r ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        listOfNotNull(r.from.ifBlank { null }, r.source, ISODates.parse(r.at)?.let { Format.relative(it) }).joinToString(" · "),
                                        style = AxType.footnoteBold, color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(ChatText.preview(r.text, 140), style = AxType.body, color = Theme.ax.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Text("답할 것이 없어요", style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
                today.spend?.let { spend ->
                    SectionLabel("오늘 쓴 돈", Modifier.padding(top = 10.dp))
                    Panel(padding = 0.dp) {
                        Text(
                            if (spend.count == 0) "오늘은 쓴 돈이 없어요" else "${Format.won(spend.amount)} · ${spend.count}건",
                            style = AxType.body, color = Theme.ax.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
