package dev.axkit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.Format
import dev.axkit.core.HealthView

private data class HealthLook(val icon: ImageVector, val color: Color, val word: String)

@Composable
private fun look(status: String): HealthLook = when (status) {
    "ok" -> HealthLook(Icons.Rounded.CheckCircle, SystemColor.green, "괜찮아요")
    "warn" -> HealthLook(Icons.Rounded.Warning, SystemColor.orange, "봐 주세요")
    "error" -> HealthLook(Icons.Rounded.Cancel, SystemColor.red, "문제가 있어요")
    else -> HealthLook(Icons.Rounded.HelpOutline, Theme.ax.secondary, "알 수 없어요")
}

private fun rank(s: String) = when (s) { "error" -> 0; "warn" -> 1; "ok" -> 3; else -> 2 }

@Composable
private fun CheckRow(c: HealthView.Check) {
    val l = look(c.status)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${c.label}, ${l.word}" + (c.detail?.let { ", $it" } ?: "") },
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(l.icon, null, Modifier.size(22.dp), tint = l.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(c.label, style = AxType.body, color = Theme.ax.label)
            c.detail?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
            c.group?.let { Text(it, style = AxType.caption, color = Theme.ax.tertiary) }
        }
    }
}

/** 상태 점검: the Mac checks itself (safe checks only) and says in plain words what needs a look; what is fine is folded away. */
@Composable
fun HealthScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<HealthView>>(Loadable.Loading) }
    var showFine by rememberSaveable { mutableStateOf(false) }
    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(HealthView.serializer(), "health") }
    }
    LaunchedEffect(Unit) { load() }

    Screen("상태 점검") {
        LoadableView(state, retry = { load() }) { h ->
            MoreRefresh({ load() }) {
                Page {
                    val bad = h.checks.count { it.status != "ok" }
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val top = look(if (bad == 0) "ok" else if (h.checks.any { it.status == "error" }) "error" else "warn")
                            Icon(top.icon, null, Modifier.size(36.dp), tint = top.color)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(h.summary ?: if (bad == 0) "모두 괜찮아요" else "${h.checks.size}가지 중 ${bad}가지를 봐 주세요", style = AxType.headline, color = Theme.ax.label)
                                h.checkedAt?.let { Text("${Format.relative(it)} 점검", style = AxType.footnote, color = Theme.ax.secondary) }
                            }
                        }
                    }
                    val problems = h.checks.filter { it.status != "ok" }.sortedBy { rank(it.status) }
                    if (problems.isNotEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader("봐야 할 것")
                                CardList(problems) { CheckRow(it) }
                            }
                        }
                    }
                    val fine = h.checks.filter { it.status == "ok" }
                    if (fine.isNotEmpty()) {
                        Panel {
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { showFine = !showFine }
                                    .semantics { stateDescription = if (showFine) "펼침" else "접힘" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("괜찮은 것 ${fine.size}개", style = AxType.body, color = Theme.ax.accent, modifier = Modifier.weight(1f))
                                Icon(SfIcons.of(if (showFine) "chevron.down" else "chevron.right"), null, Modifier.size(16.dp), tint = Theme.ax.accent)
                            }
                            AnimatedVisibility(showFine) {
                                Column(Modifier.padding(top = 8.dp)) { CardList(fine) { CheckRow(it) } }
                            }
                        }
                    }
                    if (bad > 0) {
                        ActionButton("비서에게 고쳐 달라고 하기", kind = ActionKind.Primary) {
                            val items = h.checks.filter { it.status != "ok" }.map { it.label + (it.detail?.let { d -> "($d)" } ?: "") }
                            model.send("상태 점검에서 봐야 할 것이 있어요: " + items.joinToString(", ") + ". 고칠 수 있는 건 고쳐 주세요.", "")
                            model.toast = "비서에게 보냈어요."
                        }
                    }
                }
            }
        }
    }
}
