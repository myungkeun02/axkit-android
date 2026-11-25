package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BotAvatar
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loaded
import dev.axkit.core.CalendarView
import dev.axkit.core.Calendars
import dev.axkit.core.Format
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 캘린더: plans (blue) and what got done (green) on one month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val model = LocalModel.current
    val todayYmd = calendarToday()
    var month by rememberSaveable { mutableStateOf(todayYmd.take(7)) }
    var selected by rememberSaveable { mutableStateOf(todayYmd) }
    var state by remember { mutableStateOf<Loadable<CalendarView>>(Loadable.Loading) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val caps = model.capabilities
    // A Mac that lists its views but not `calendar` has no calendar yet; one that lists none is still loading.
    val missing = caps.views.isNotEmpty() && !caps.hasView("calendar")
    val canAdd = caps.view("calendar")?.actions?.contains("add") == true

    suspend fun load() {
        val client = model.client ?: return
        val want = month
        if (state.loaded?.month != want) state = Loadable.Loading
        try {
            state = Loadable.Loaded(client.view(CalendarView.serializer(), "calendar", mapOf("month" to want)))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            state = Loadable.Failed(e.friendly)
        }
    }

    LaunchedEffect(month, missing, model.client) { if (!missing) load() }

    fun shift(months: Long) {
        month = YearMonth.parse(month).plusMonths(months).toString()
        val today = calendarToday()
        selected = if (month == today.take(7)) today else "$month-01"
    }

    Screen(
        "캘린더", large = true,
        trailing = if (missing) null else {
            {
                CalendarBarButtons(
                    todayEnabled = selected != todayYmd,
                    showAdd = canAdd,
                    onToday = { selected = calendarToday(); month = selected.take(7) },
                    onAdd = { adding = true },
                )
            }
        },
    ) {
        if (missing) {
            EmptyStateView(
                SfIcons.of("calendar"), "맥에 아직 캘린더가 없어요",
                "맥의 ax-kit를 새 버전으로 올리면 여기에 일정이 보여요.",
            )
            return@Screen
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; try { load() } finally { refreshing = false } } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Page {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MonthHeader(month, onPrev = { shift(-1) }, onNext = { shift(1) })
                    Box(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                        LoadableView(state, retry = { load() }) { data ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MonthGrid(month, selected, todayYmd, data) { selected = it }
                                Legend(data.calendars)
                                if (data.calendarsLimited == true) CalendarsLimitedNote()
                                DayDetail(selected, data.day(selected), data.calendars)
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) NewEventSheet(
        day = selected,
        calendars = state.loaded?.calendars,
        onClose = { adding = false },
        done = { added ->
            if (added != null) { selected = added; month = added.take(7) }
            load()
        },
    )
}

internal fun calendarToday(): String = Format.ymd(LocalDate.now(Format.seoul))

/** "오늘로" and "＋" side by side in one floating glass capsule, like the iOS 26 toolbar group. */
@Composable
private fun CalendarBarButtons(todayEnabled: Boolean, showAdd: Boolean, onToday: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(CircleShape)
            .background(Theme.ax.card.copy(alpha = 0.96f))
            .border(0.5.dp, Theme.ax.separator.copy(alpha = 0.4f), CircleShape)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight44().clickable(enabled = todayEnabled, role = Role.Button, onClick = onToday).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("오늘로", style = AxType.body, color = if (todayEnabled) Theme.ax.label else Theme.ax.tertiary) }
        if (showAdd) {
            Box(
                Modifier.fillMaxHeight44().widthIn(min = 44.dp).clickable(role = Role.Button, onClick = onAdd).padding(start = 4.dp, end = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(SfIcons.of("plus"), "일정 추가", Modifier.size(24.dp), tint = Theme.ax.label) }
        }
    }
}

private fun Modifier.fillMaxHeight44() = this.height(44.dp)

/** iOS .action(.secondary, small): light accent rounded square with a chevron. */
@Composable
private fun SmallChevronButton(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Theme.ax.accent.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(SfIcons.of(icon), label, Modifier.size(20.dp), tint = Theme.ax.accent) }
}

@Composable
private fun MonthHeader(month: String, onPrev: () -> Unit, onNext: () -> Unit) {
    val parts = month.split("-")
    val title = "${parts.firstOrNull() ?: ""}년 ${parts.lastOrNull()?.toIntOrNull() ?: 0}월"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SmallChevronButton("chevron.left", "지난달", onPrev)
        Text(title, style = AxType.title2, color = Theme.ax.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        SmallChevronButton("chevron.right", "다음 달", onNext)
    }
}

/** §36: a stable color per calendar; slot 0 (the main calendar) is the usual accent. Green stays for "한 일". */
@Composable
internal fun calendarColor(slot: Int): Color = when (slot) {
    0 -> Theme.ax.accent
    1 -> SystemColor.purple
    2 -> SystemColor.orange
    3 -> SystemColor.pink
    4 -> SystemColor.teal
    5 -> SystemColor.indigo
    6 -> SystemColor.brown
    else -> SystemColor.mint
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(calendars: List<CalendarView.Calendar>?) {
    val legend = Calendars.legend(calendars)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (legend.isEmpty()) LegendItem(Theme.ax.accent, "일정")
        else legend.forEach { LegendItem(calendarColor(Calendars.slotIn(it, calendars)), it.name) }
        LegendItem(SystemColor.green, "한 일")
    }
}

@Composable
private fun CalendarsLimitedNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(SfIcons.of("info.circle"), null, Modifier.size(15.dp), tint = Theme.ax.secondary)
        Text(Calendars.LIMITED, style = AxType.footnote, color = Theme.ax.secondary)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = AxType.caption, color = Theme.ax.secondary)
    }
}

@Composable
private fun MonthGrid(month: String, selected: String, today: String, data: CalendarView, onSelect: (String) -> Unit) {
    val ym = YearMonth.parse(month)
    val lead = ym.atDay(1).dayOfWeek.value % 7 // Sunday first
    val count = ym.lengthOfMonth()
    val cells: List<Int?> = List(lead) { null } + (1..count).map { it }
    Panel(padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, name ->
                    Text(
                        name, style = AxType.captionBold, color = if (i == 0) SystemColor.red else Theme.ax.secondary,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            for (week in cells.chunked(7)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f)) {
                            if (day != null) {
                                val ymd = "$month-${day.toString().padStart(2, '0')}"
                                DayCell(day, ymd == today, ymd == selected, data.day(ymd), data.calendars) { onSelect(ymd) }
                            } else {
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean, isSelected: Boolean, data: CalendarView.Day?, calendars: List<CalendarView.Calendar>?, onTap: () -> Unit) {
    val events = data?.events?.size ?: 0
    val done = data?.done?.size ?: 0
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = "${day}일, 일정 ${events}개, 한 일 ${done}개"
                role = Role.Button
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (isSelected) Theme.ax.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                style = AxType.callout.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                color = if (isSelected) Color.White else if (isToday) Theme.ax.accent else Theme.ax.label,
            )
        }
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // One dot per calendar that has a plan that day (at most three).
            data?.events.orEmpty().map { Calendars.slotOf(it, calendars) }.distinct().take(3).forEach { slot ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(calendarColor(slot)))
            }
            if (done > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(SystemColor.green))
        }
    }
}

@Composable
private fun DayDetail(selected: String, day: CalendarView.Day?, calendars: List<CalendarView.Calendar>?) {
    val parts = selected.split("-")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "${parts.getOrNull(1)?.toIntOrNull() ?: 0}월 ${parts.getOrNull(2)?.toIntOrNull() ?: 0}일",
            style = AxType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label,
        )
        val events = day?.events.orEmpty()
        val done = day?.done.orEmpty()
        if (events.isEmpty() && done.isEmpty()) {
            Text("이날은 일정도, 한 일도 없어요.", style = AxType.body, color = Theme.ax.secondary)
        }
        if (events.isNotEmpty()) Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader("일정")
                for (e in events) {
                    Row(
                        Modifier.clearAndSetSemantics {
                            contentDescription = listOfNotNull(if (e.allDay == true) "종일" else Format.clock(e.start), e.title, e.place, Calendars.label(e)).joinToString(", ")
                        },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (e.allDay == true) "종일" else Format.clock(e.start),
                            style = AxType.subheadline.copy(fontFeatureSettings = "tnum"), color = Theme.ax.secondary,
                            modifier = Modifier.width(76.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(e.title, style = AxType.body.copy(fontWeight = FontWeight.Medium), color = Theme.ax.label)
                            if (e.place != null) Text(e.place!!, style = AxType.footnote, color = Theme.ax.secondary)
                            // §36: which calendar (and account) the plan is on, with its legend color.
                            Calendars.label(e)?.let { label ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(calendarColor(Calendars.slotOf(e, calendars))))
                                    Text(label, style = AxType.footnote, color = Theme.ax.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (done.isNotEmpty()) Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader("한 일")
                for (d in done) {
                    val who = d.bot?.name ?: "비서"
                    Row(
                        Modifier.clearAndSetSemantics { contentDescription = "${d.title}, $who · ${Format.clock(d.at)}" },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BotAvatar(who, size = 28.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.title, style = AxType.body.copy(fontWeight = FontWeight.Medium), color = Theme.ax.label)
                            Text("$who · ${Format.clock(d.at)}", style = AxType.footnote, color = Theme.ax.secondary)
                        }
                    }
                }
            }
        }
    }
}
