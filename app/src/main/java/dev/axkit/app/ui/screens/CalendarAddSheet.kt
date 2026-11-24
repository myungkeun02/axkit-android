package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.Format
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 일정 추가: title, day, time (or all day), place. Saved to the Mac's calendar. */
@Composable
fun NewEventSheet(day: String, onClose: () -> Unit, done: suspend (String?) -> Unit) {
    val model = LocalModel.current
    val scope = model.scope
    // Start on the selected day at the next whole hour, one hour long.
    val initial = remember(day) {
        val hour = minOf(ZonedDateTime.now(Format.seoul).hour + 1, 23)
        val date = runCatching { LocalDate.parse(day) }.getOrElse { LocalDate.now(Format.seoul) }
        date.atTime(hour, 0).atZone(Format.seoul)
    }
    var title by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(initial) }
    var end by remember { mutableStateOf(initial.plusHours(1)) }
    var saving by remember { mutableStateOf(false) }

    fun setStart(s: ZonedDateTime) { start = s; if (!end.isAfter(s)) end = s.plusHours(1) }
    fun setEnd(e: ZonedDateTime) { end = if (e.isBefore(start)) start else e }

    fun save() {
        val client = model.client ?: return
        saving = true
        scope.launch {
            try {
                val iso = { z: ZonedDateTime -> z.toInstant().truncatedTo(ChronoUnit.SECONDS).toString() }
                val body = buildJsonObject {
                    put("title", JsonPrimitive(title.trim()))
                    put("allDay", JsonPrimitive(allDay))
                    put("start", JsonPrimitive(if (allDay) Format.ymd(start.toLocalDate()) else iso(start)))
                    if (!allDay) put("end", JsonPrimitive(iso(end)))
                    if (place.trim().isNotEmpty()) put("place", JsonPrimitive(place.trim()))
                }
                client.viewAction("calendar", "add", body)
                model.toast = "캘린더에 넣었어요"
                done(Format.ymd(start.toLocalDate()))
                onClose()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                model.toast = e.friendly
            } finally {
                saving = false
            }
        }
    }

    AxSheet(onDismiss = onClose) {
        SheetBar("일정 추가", leading = { BarButton("취소", onClick = onClose) })
        Page(top = 8.dp) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlainField(title, { title = it }, "무슨 일정인가요? (예: 치과)", AxType.title3)
                    Hairline()
                    PlainField(place, { place = it }, "어디서 (안 적어도 돼요)", AxType.body)
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("하루 종일", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = allDay, onCheckedChange = { allDay = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Theme.ax.accent, checkedThumbColor = Color.White,
                                uncheckedTrackColor = Theme.ax.fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                    Hairline()
                    DateTimeRow("시작", start, showTime = !allDay, minDate = null, onChange = ::setStart)
                    if (!allDay) {
                        Hairline()
                        DateTimeRow("끝", end, showTime = true, minDate = start.toLocalDate(), onChange = ::setEnd)
                    }
                }
            }
            Box {
                ActionButton(
                    "캘린더에 넣기", kind = ActionKind.Primary,
                    enabled = title.trim().isNotEmpty() && !saving,
                ) { save() }
                if (saving) CircularProgressIndicator(
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(20.dp), color = Color.White, strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle) {
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = style.copy(color = Theme.ax.label),
        cursorBrush = SolidColor(Theme.ax.accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Theme.ax.tertiary)
                inner()
            }
        },
    )
}

/** iOS compact DatePicker: label on the left, grey capsules for the day and the time that open a picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(label: String, value: ZonedDateTime, showTime: Boolean, minDate: LocalDate?, onChange: (ZonedDateTime) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = AxType.body, color = Theme.ax.label)
        Spacer(Modifier.weight(1f))
        PickerChip("${value.year}. ${value.monthValue}. ${value.dayOfMonth}.") { pickDate = true }
        if (showTime) PickerChip(Format.clock(value.toInstant())) { pickTime = true }
    }

    if (pickDate) {
        val minMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = minMillis == null || utcTimeMillis >= minMillis
            },
        )
        val colors = DatePickerDefaults.colors(containerColor = Theme.ax.card)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                BarButton("완료", bold = true) {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(value.with(d))
                    }
                    pickDate = false
                }
            },
            dismissButton = { BarButton("취소") { pickDate = false } },
            colors = colors,
        ) { DatePicker(state, colors = colors, showModeToggle = false) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Theme.ax.card,
            confirmButton = {
                BarButton("완료", bold = true) {
                    onChange(value.with(LocalTime.of(state.hour, state.minute)))
                    pickTime = false
                }
            },
            dismissButton = { BarButton("취소") { pickTime = false } },
            text = {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Theme.ax.fill, selectorColor = Theme.ax.accent,
                        timeSelectorSelectedContainerColor = Theme.ax.accent.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = Theme.ax.accent,
                        periodSelectorSelectedContainerColor = Theme.ax.accent.copy(alpha = 0.15f),
                        periodSelectorSelectedContentColor = Theme.ax.accent,
                    ),
                )
            },
        )
    }
}

@Composable
private fun PickerChip(text: String, onClick: () -> Unit) {
    Text(
        text, style = AxType.body, color = Theme.ax.label,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Theme.ax.fill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}
