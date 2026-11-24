package dev.axkit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxSwitch
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.KV
import dev.axkit.app.ui.design.KVCard
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.app.ui.design.loaded
import dev.axkit.app.ui.design.safeTap
import dev.axkit.core.Format
import dev.axkit.core.SpendingView
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** 지출 관리: this month's total against the budget, by category, and every entry (tap to fix its category). */
@Composable
fun SpendingScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var month by rememberSaveable { mutableStateOf(Format.ymd(Instant.now()).take(7)) }
    var state by remember { mutableStateOf<Loadable<SpendingView>>(Loadable.Loading) }
    var editing by remember { mutableStateOf<SpendingView.Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var budgetText by remember { mutableStateOf("") }
    var editingBudget by remember { mutableStateOf(false) }

    fun can(action: String) = model.capabilities.view("spending")?.actions?.contains(action) == true

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(SpendingView.serializer(), "spending", mapOf("month" to month)) }
    }

    fun shift(n: Int) {
        month = runCatching { YearMonth.parse(month).plusMonths(n.toLong()).toString() }.getOrDefault(month)
    }

    suspend fun saveBudget() {
        val client = model.client ?: return
        val digits = budgetText.filter { it.isDigit() }
        val value = digits.toDoubleOrNull()
        try {
            client.viewAction("spending", "budget", buildJsonObject { put("monthly", if (value != null) JsonPrimitive(value) else JsonNull) })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { model.toast = e.friendly }
        load()
    }

    LaunchedEffect(month) { load() }
    Screen(
        "지출 관리", large = true,
        trailing = {
            MoreGlassGroup(buildList {
                add(Triple(MoreChevronLeft, "지난달") { shift(-1) })
                add(Triple(MoreChevronRight, "다음 달") { shift(1) })
                if (can("add")) add(Triple(Icons.Rounded.Add, "직접 적기") { adding = true })
            })
        },
    ) {
        LoadableView(state, retry = { load() }) { s ->
            MoreRefresh({ load() }) {
                Page {
                    Panel {
                        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${s.month.takeLast(2).toIntOrNull() ?: 0}월에 쓴 돈", style = AxType.subheadline, color = Theme.ax.secondary)
                            Text(Format.won(s.total), style = AxType.largeTitle.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
                            val budget = s.budget
                            if (budget != null && budget > 0) {
                                MoreProgressBar(
                                    minOf(s.total / budget, 1.0),
                                    if (s.total > budget) SystemColor.red else if (s.total > budget * 0.8) SystemColor.orange else SystemColor.green,
                                )
                                Text(
                                    if (s.total > budget) "예산보다 ${Format.won(s.total - budget)} 더 썼어요" else "예산 ${Format.won(budget)} 중 ${Format.won(budget - s.total)} 남았어요",
                                    style = AxType.footnote, color = Theme.ax.secondary,
                                )
                            }
                            val prev = s.previousTotal
                            if (prev != null && prev > 0) {
                                val diff = s.total - prev
                                Text(
                                    if (diff >= 0) "지난달보다 ${Format.won(diff)} 더 썼어요" else "지난달보다 ${Format.won(-diff)} 덜 썼어요",
                                    style = AxType.footnote, color = Theme.ax.secondary,
                                )
                            }
                            if (can("budget")) {
                                ActionButton(
                                    if (s.budget == null) "예산 정하기" else "예산 바꾸기",
                                    modifier = Modifier.padding(top = 4.dp), kind = ActionKind.Secondary, fullWidth = false, small = true,
                                ) {
                                    budgetText = s.budget?.toLong()?.toString() ?: ""
                                    editingBudget = true
                                }
                            }
                        }
                    }
                    if (s.byCategory.isNotEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CardHeader("어디에 썼나")
                                val top = s.byCategory.first().amount
                                s.byCategory.forEach { c ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row {
                                            Text(c.label, style = AxType.subheadline, color = Theme.ax.label, modifier = Modifier.weight(1f))
                                            Text(Format.won(c.amount), style = AxType.subheadline.merge(TabularNums), color = Theme.ax.secondary)
                                        }
                                        MoreProgressBar(c.amount / maxOf(top, 1.0))
                                    }
                                }
                            }
                        }
                    }
                    val subs = s.subscriptions
                    if (!subs.isNullOrEmpty()) {
                        KVCard("매달 나가는 돈", subs.map { sub ->
                            KV(sub.merchant, Format.won(sub.amount) + (sub.nextDate?.let { " · " + it.takeLast(5).replace("-", "/") } ?: ""))
                        })
                    }
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("내역")
                            CardList(s.entries ?: emptyList()) { e ->
                                val tappable = can("categorize")
                                CardRow(
                                    e.merchant, detail = "${e.date.takeLast(5).replace("-", "/")} · ${e.categoryLabel ?: "분류 없음"}",
                                    modifier = Modifier.safeTap { if (tappable) editing = e }
                                        .then(if (tappable) Modifier.semantics { onClick(label = "눌러서 분류를 바꿔요") { editing = e; true } } else Modifier),
                                ) {
                                    Text(Format.won(e.amount), style = AxType.body.merge(TabularNums), color = if (e.amount < 0) SystemColor.green else Theme.ax.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        CategorySheet(e, state.loaded?.categories ?: emptyList(), onClose = { editing = null }) { load() }
    }
    if (adding) AddEntrySheet(state.loaded?.categories ?: emptyList(), onClose = { adding = false }) { load() }
    if (editingBudget) {
        MoreAlert(
            title = "한 달 예산",
            buttons = listOf(
                MoreAlertButton("저장") { scope.launch { saveBudget() } },
                MoreAlertButton("예산 없애기", MoreAlertButton.Role.Destructive) { budgetText = ""; scope.launch { saveBudget() } },
                MoreAlertButton("취소", MoreAlertButton.Role.Cancel) {},
            ),
            onDismiss = { editingBudget = false },
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(7.dp)).background(Theme.ax.page).padding(horizontal = 8.dp),
            ) {
                MoreField(budgetText, { budgetText = it }, "예: 1200000", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), style = AxType.subheadline)
            }
        }
    }
}

@Composable
private fun CategorySheet(entry: SpendingView.Entry, categories: List<SpendingView.CategoryChoice>, onClose: () -> Unit, done: suspend () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var keep by remember { mutableStateOf(true) }
    AxSheet(onDismiss = onClose) {
        SheetBar("분류 바꾸기", leading = { BarButton("닫기") { onClose() } })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MoreFormSection {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(entry.merchant, style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                    Text(Format.won(entry.amount), style = AxType.body, color = Theme.ax.secondary)
                }
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("다음부터 '${entry.merchant}'은 늘 이 분류로", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                    AxSwitch(keep) { keep = it }
                }
            }
            MoreFormSection(header = "분류 고르기") {
                categories.forEachIndexed { i, c ->
                    if (i > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                try {
                                    model.client?.viewAction("spending", "categorize", buildJsonObject {
                                        put("entryId", JsonPrimitive(entry.id)); put("category", JsonPrimitive(c.id)); put("remember", JsonPrimitive(keep))
                                    })
                                    model.toast = "'${c.label}'(으)로 바꿨어요"
                                } catch (e: CancellationException) { throw e } catch (e: Exception) { model.toast = e.friendly }
                                done()
                                onClose()
                            }
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.label, style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                        if (c.id == entry.category) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = Theme.ax.accent)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntrySheet(categories: List<SpendingView.CategoryChoice>, onClose: () -> Unit, done: suspend () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now(Format.seoul)) }
    var category by remember { mutableStateOf("other") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    val amountValue = amount.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0

    AxSheet(onDismiss = onClose) {
        SheetBar(
            "현금 지출 적기",
            leading = { BarButton("취소") { onClose() } },
            trailing = {
                BarButton("적기", bold = true, enabled = merchant.isNotEmpty() && amountValue > 0) {
                    scope.launch {
                        try {
                            model.client?.viewAction("spending", "add", buildJsonObject {
                                put("merchant", JsonPrimitive(merchant)); put("amount", JsonPrimitive(amountValue))
                                put("date", JsonPrimitive(Format.ymd(date))); put("category", JsonPrimitive(category))
                            })
                            model.toast = "적었어요"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { model.toast = e.friendly }
                        done()
                        onClose()
                    }
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
            MoreFormSection {
                MoreField(merchant, { merchant = it }, "어디서 (예: 시장)")
                Hairline()
                MoreField(amount, { amount = it }, "얼마 (원)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("언제", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                    Text(
                        "${date.year}. ${date.monthValue}. ${date.dayOfMonth}.", style = AxType.body, color = Theme.ax.label,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Theme.ax.fill).clickable { pickingDate = true }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
                Hairline()
                Box {
                    Row(Modifier.fillMaxWidth().clickable { pickingCategory = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("분류", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                        Text(categories.firstOrNull { it.id == category }?.label ?: "", style = AxType.body, color = Theme.ax.secondary)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(16.dp), tint = Theme.ax.secondary)
                    }
                    DropdownMenu(pickingCategory, onDismissRequest = { pickingCategory = false }, modifier = Modifier.background(Theme.ax.card)) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.label, style = AxType.body, color = Theme.ax.label) },
                                leadingIcon = { if (c.id == category) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Theme.ax.label) else Spacer(Modifier.size(18.dp)) },
                                onClick = { category = c.id; pickingCategory = false },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("완료", style = AxType.bodyBold, color = Theme.ax.accent) }
            },
            dismissButton = { TextButton({ pickingDate = false }) { Text("취소", style = AxType.body, color = Theme.ax.accent) } },
        ) { DatePicker(picker) }
    }
}
