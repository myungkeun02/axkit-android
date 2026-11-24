package dev.axkit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.KV
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.Format
import dev.axkit.core.UsageView
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** 봇·AI 사용량: how often the assistant asked an AI, what it cost, per bot and per model. */
@Composable
fun UsageScreen() {
    val model = LocalModel.current
    var period by rememberSaveable { mutableStateOf("day") }
    var state by remember { mutableStateOf<Loadable<UsageView>>(Loadable.Loading) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(UsageView.serializer(), "usage", mapOf("period" to period)) }
    }

    LaunchedEffect(period) { load() }
    Screen("봇·AI 사용량", large = true) {
        Column(
            Modifier.fillMaxSize().background(Theme.ax.page).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoreSegmented(listOf("day" to "오늘", "week" to "최근 7일", "month" to "이번 달"), period) { period = it }
            val loaded = state as? Loadable.Loaded
            if (loaded != null) UsageContent(loaded.value)
            else Box(Modifier.fillMaxWidth().height(300.dp)) { LoadableView(state, retry = { load() }) {} }
        }
    }
}

@Composable
private fun UsageContent(u: UsageView) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI에게 ${u.totals.calls}번 물어봤어요", style = AxType.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Theme.ax.label)
                Text("쓴 돈: ${Format.dollars(u.totals.costUsd)} (약 ${Format.won(u.totals.costUsd * 1400)})", style = AxType.subheadline, color = Theme.ax.label)
                val all = u.totals.calls + u.totals.failures
                val failRate = if (all == 0) 0.0 else u.totals.failures.toDouble() / all
                Text(
                    if (failRate < 0.05) "거의 다 잘 됐어요" else "실패가 조금 많아요 (${u.totals.failures}번). AI 연결하기를 확인해 보세요.",
                    style = AxType.footnote, color = Theme.ax.secondary,
                )
                val budget = u.budget
                if (budget != null) {
                    val limit = if (u.period == "month") budget.monthlyUsd else budget.dailyUsd
                    if (limit > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("예산 ${Format.dollars(limit)} 중 ${(u.totals.costUsd / limit * 100).toInt()}%", style = AxType.caption, color = Theme.ax.label)
                            MoreProgressBar(minOf(u.totals.costUsd / limit, 1.0), if (u.totals.costUsd > limit * 0.8) SystemColor.orange else Theme.ax.accent)
                        }
                    }
                }
            }
        }
        val daily = u.daily
        if (!daily.isNullOrEmpty()) {
            Panel {
                Text(if (u.period == "day") "시간별" else "날짜별", style = AxType.headline, color = Theme.ax.label)
                Spacer(Modifier.height(8.dp))
                val day = u.period == "day"
                val keys = daily.map { it.date.takeLast(if (day) 5 else 2) }
                // A few buckets would draw one fat bar across the card: keep bars slim and label the ends.
                val shown = if (keys.size <= 8) keys.indices.toSet() else setOf(0, keys.lastIndex)
                UsageBarChart(
                    daily.map { it.calls },
                    keys.mapIndexed { i, k -> if (i in shown) usageBucketLabel(k, day) else null },
                    Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        val bots = u.byBot
        if (!bots.isNullOrEmpty()) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("봇별", style = AxType.headline, color = Theme.ax.label)
                    val maxCalls = (bots.maxOfOrNull { it.calls } ?: 1).coerceAtLeast(1).toDouble()
                    bots.forEach { b ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(b.name, style = AxType.subheadline, color = Theme.ax.label, modifier = Modifier.weight(1f))
                                Text("${b.calls}번 · ${Format.dollars(b.costUsd)}", style = AxType.subheadline, color = Theme.ax.secondary)
                            }
                            MoreProgressBar(b.calls / maxCalls)
                        }
                    }
                }
            }
        }
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardHeader("AI 모델별")
                CardList(u.byModel.map { KV(it.model, "${it.calls}번 · ${Format.dollars(it.costUsd)}") }) { row ->
                    CardRow(usageModelName(row.title), detail = usageProviderName(u.byModel.firstOrNull { it.model == row.title }?.providerId)) {
                        Text(row.value, style = AxType.body, color = Theme.ax.secondary)
                    }
                }
            }
        }
    }
}

/** "09:00" → "9시", "20" → "20일". */
fun usageBucketLabel(raw: String, day: Boolean): String {
    if (day) raw.take(2).toIntOrNull()?.let { return "${it}시" }
    raw.toIntOrNull()?.let { return "${it}일" }
    return raw
}

/** Swift Charts look: slim accent bars with a soft gradient, light grid lines with numbers on the right, labels under some bars. */
@Composable
private fun UsageBarChart(values: List<Int>, labels: List<String?>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val accent = Theme.ax.accent
    val grid = Theme.ax.separator.copy(alpha = 0.35f)
    val labelStyle = AxType.caption2.copy(color = Theme.ax.secondary)
    val ticks = remember(values) { niceTicks(values.maxOrNull() ?: 0) }
    val top = ticks.last().toFloat().coerceAtLeast(1f)
    Canvas(modifier.clearAndSetSemantics { contentDescription = "막대 그래프" }) {
        val labelWidth = ticks.maxOf { measurer.measure(it.toString(), labelStyle).size.width }.toFloat()
        val gap = 6.dp.toPx()
        val plotRight = size.width - labelWidth - gap
        val axisHeight = if (labels.any { it != null }) measurer.measure("0", labelStyle).size.height + 4.dp.toPx() else 0f
        val plotHeight = size.height - axisHeight
        ticks.forEach { t ->
            val y = plotHeight - plotHeight * (t / top)
            drawLine(grid, Offset(0f, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(t.toString(), labelStyle)
            val ly = (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height / 2f)
            drawText(layout, topLeft = Offset(plotRight + gap, ly))
        }
        if (values.isEmpty()) return@Canvas
        val slot = plotRight / values.size
        val barWidth = minOf(slot * 0.66f, (if (values.size < 8) 22 else 10).dp.toPx())
        labels.forEachIndexed { i, label ->
            if (label == null) return@forEachIndexed
            val layout = measurer.measure(label, labelStyle)
            val x = (i * slot + slot / 2f - layout.size.width / 2f).coerceIn(0f, plotRight - layout.size.width)
            drawText(layout, topLeft = Offset(x, plotHeight + 4.dp.toPx()))
        }
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val h = plotHeight * (v / top)
            val x = i * slot + (slot - barWidth) / 2f
            drawRect(
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.78f), accent), startY = plotHeight - h, endY = plotHeight),
                topLeft = Offset(x, plotHeight - h), size = Size(barWidth, h),
            )
        }
    }
}

/** 0, 20, 40, 60 — about four round steps above the biggest value. */
private fun niceTicks(max: Int): List<Int> {
    if (max <= 0) return listOf(0, 1)
    val raw = max / 3.0
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }.let { if (it < 1) 1.0 else ceil(it) }.toInt()
    val top = ((max + step - 1) / step) * step
    return (0..top step step).toList()
}

/** "claude-sonnet-5-5" → "Claude Sonnet 5.5", "gpt-5.4-mini" → "GPT 5.4 mini". */
fun usageModelName(id: String): String {
    val out = mutableListOf<String>()
    val digits = mutableListOf<String>()
    val known = mapOf("gpt" to "GPT", "deepseek" to "DeepSeek", "mini" to "mini", "nano" to "nano", "pro" to "Pro")
    for (p in id.split("-")) {
        if (p.isEmpty()) continue
        if (p.all { it.isDigit() }) { digits.add(p); continue }
        if (digits.isNotEmpty()) { out.add(digits.joinToString(".")); digits.clear() }
        out.add(known[p.lowercase()] ?: if (p.first().isDigit()) p else p.take(1).uppercase() + p.drop(1))
    }
    if (digits.isNotEmpty()) out.add(digits.joinToString("."))
    return out.joinToString(" ")
}

fun usageProviderName(id: String?): String? {
    if (id == null) return null
    return mapOf("anthropic" to "Claude", "openai" to "ChatGPT", "deepseek" to "DeepSeek", "google" to "Gemini", "gemini" to "Gemini", "ollama" to "맥 안의 AI")[id] ?: id
}
