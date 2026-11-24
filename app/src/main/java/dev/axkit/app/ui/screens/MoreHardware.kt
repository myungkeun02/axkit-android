package dev.axkit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.KV
import dev.axkit.app.ui.design.KVCard
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loaded
import dev.axkit.core.Format
import dev.axkit.core.HardwareView
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

/** 맥 상태: how hard the Mac is working, refreshed every 15 seconds while open. */
@Composable
fun HardwareScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<HardwareView>>(Loadable.Loading) }

    suspend fun load() {
        val client = model.client ?: return
        try { state = Loadable.Loaded(client.view(HardwareView.serializer(), "hardware")) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (state.loaded == null) state = Loadable.Failed(e.friendly) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(15_000)
        }
    }
    Screen("맥 상태", large = true) {
        LoadableView(state, retry = { load() }) { h ->
            MoreRefresh({ load() }) {
                Page {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(h.machine.name, style = AxType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
                            h.summary?.let { Text(it, style = AxType.body, color = Theme.ax.secondary) }
                        }
                    }
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardHeader("지금")
                            h.cpuPercent?.let { cpu -> Meter("일하는 정도(CPU)", cpu / 100, "${cpu.toInt()}%") }
                            h.memory?.let { m -> Meter("기억 공간(메모리)", m.used / m.total, "${fmt(m.used)} / ${fmt(m.total)} ${m.unit}") }
                            h.disk?.let { d -> Meter("저장 공간", d.used / d.total, "${fmt(d.total - d.used)} ${d.unit} 남음") }
                        }
                    }
                    val (tWords, tColor) = thermalWords(h.thermal)
                    KVCard("맥", listOfNotNull(
                        KV("온도", tWords, tColor),
                        h.power?.let { KV("전원", it) },
                        h.machine.model?.let { KV("기종", it) },
                        h.machine.os?.let { KV("운영체제", it) },
                        h.machine.uptimeSec?.let { KV("켜진 지", Format.duration(it)) },
                    ))
                    val net = h.network
                    if (!net.isNullOrEmpty()) KVCard("네트워크", net.entries.sortedBy { it.key }.map { KV(label(it.key), it.value) })
                    val server = h.server
                    if (!server.isNullOrEmpty()) KVCard("비서 프로그램", server.entries.sortedBy { it.key }.map { KV(label(it.key), it.value) })
                    h.checkedAt?.let { at ->
                        Text("${Format.relative(at)} 확인", style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun thermalWords(s: String?): Pair<String, Color> = when (s) {
    "nominal" -> "시원해요" to SystemColor.green
    "fair" -> "조금 따뜻해요" to SystemColor.yellow
    "serious" -> "뜨거워요 — 일이 느려질 수 있어요" to SystemColor.orange
    "critical" -> "너무 뜨거워요!" to SystemColor.red
    else -> "알 수 없어요" to Theme.ax.secondary
}

private fun label(key: String): String =
    mapOf("tailscale" to "Tailscale", "internet" to "인터넷", "version" to "버전", "uptime" to "켜진 지", "stop" to "전체 정지 스위치")[key] ?: key

private fun fmt(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

@Composable
private fun Meter(title: String, value: Double, detail: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(title, style = AxType.subheadline, color = Theme.ax.label, modifier = Modifier.weight(1f))
            Text(detail, style = AxType.subheadline, color = Theme.ax.secondary)
        }
        MoreProgressBar(value.coerceIn(0.0, 1.0), if (value > 0.85) SystemColor.orange else Theme.ax.accent)
    }
}
