package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.MenuRow
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.Theme

/**
 * 더보기: settings first, then the things the assistant manages. Rows appear only when the Mac has that view,
 * and server-drawn screens (ui: "blocks") show up under 새 기능 without an app update.
 */
@Composable
fun MoreView() {
    val model = LocalModel.current
    val nav = LocalNav.current
    fun has(view: String) = model.capabilities.hasView(view)

    // Screens opened by a link (`axkit://open/more?v=jobs`); taps push as usual.
    LaunchedEffect(model.moreRoute) {
        val route = model.moreRoute ?: return@LaunchedEffect
        model.moreRoute = null
        nav.popToRoot()
        nav.push("more-$route") { MoreScreen(route) }
    }

    Screen("더보기", large = true) {
        MoreRefresh({ model.refreshBasics() }) {
            Page {
                Panel {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push("settings") { SettingsScreen() } },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                            Icon(SfIcons.of("macmini"), null, Modifier.size(26.dp), tint = Theme.ax.accent)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("설정 및 연결", style = AxType.headline, color = Theme.ax.label)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(model.connection.color))
                                Text("${model.credentials?.displayHost ?: ""} · ${model.connection.label}", style = AxType.footnote, color = Theme.ax.secondary)
                            }
                        }
                        Chevron()
                    }
                }
                MoreMenu("녹음", listOf(
                    if (model.recordingFeature) MoreItem("녹음해서 회의록 만들기", "waveform") { RecordingsScreen() } else null,
                ))
                MoreMenu("맡긴 일", listOf(
                    if (has("work")) MoreItem("하고 있는 일", "hourglass") { WorkScreen() } else null,
                ))
                MoreMenu("비서가 챙기는 것", listOf(
                    if (has("people")) MoreItem("사람", "person.2.fill") { PeopleScreen() } else null,
                    if (has("autonomy")) MoreItem("알아서 하는 일", "wand.and.stars") { AutonomyScreen() } else null,
                    if (has("routines")) MoreItem("루틴", "clock.arrow.circlepath") { RoutinesScreen() } else null,
                    if (has("jobs")) MoreItem("반복 작업", "repeat") { JobsScreen() } else null,
                    if (model.capabilities.has("browser-live")) MoreItem("브라우저 화면 넘겨받기", "safari") { BrowserLiveScreen() } else null,
                    if (has("spending")) MoreItem("지출 관리", "wonsign.circle") { SpendingScreen() } else null,
                    if (model.capabilities.has("projects")) MoreItem("프로젝트 가져오기", "folder.badge.gearshape") { ProjectsScreen() } else null,
                    if (model.capabilities.has("first-look")) MoreItem("처음 알아보기", "sparkle") { FirstLookScreen() } else null,
                ))
                MoreMenu("AI", listOf(
                    if (has("usage")) MoreItem("봇·AI 사용량", "chart.bar") { UsageScreen() } else null,
                    if (has("providers")) MoreItem("AI 연결하기", "bolt.horizontal.circle") { ProvidersScreen() } else null,
                    if (has("modeltiers")) MoreItem("AI 등급과 용도", "list.number") { ModelTiersScreen() } else null,
                ))
                MoreMenu("맥", listOf(
                    MoreItem("터미널", "apple.terminal") { TerminalHostsScreen() },
                    if (has("health")) MoreItem("상태 점검", "stethoscope") { HealthScreen() } else null,
                    if (has("hardware")) MoreItem("맥 상태", "cpu") { HardwareScreen() } else null,
                ))
                val extras = model.capabilities.views.filter { it.ui == "blocks" }
                MoreMenu(
                    "새 기능",
                    extras.map { info -> MoreItem(info.title ?: info.name, info.icon ?: "sparkle") { BlocksScreen(info.name) } },
                    footer = "비서가 새로 배운 기능이에요. 앱을 업데이트하지 않아도 여기에 생겨요.",
                )
            }
        }
    }
}

/** A 더보기 screen by name — the same raw values as iOS `MoreView.screen` (links: `axkit://open/more?v=<name>`). */
@Composable
fun MoreScreen(name: String) {
    when (name) {
        "settings" -> SettingsScreen()
        "recordings" -> RecordingsScreen()
        "autonomy" -> AutonomyScreen()
        "routines" -> RoutinesScreen()
        "jobs" -> JobsScreen()
        "spending" -> SpendingScreen()
        "usage" -> UsageScreen()
        "providers" -> ProvidersScreen()
        "modeltiers" -> ModelTiersScreen()
        "health" -> HealthScreen()
        "hardware" -> HardwareScreen()
        "browser-live" -> BrowserLiveScreen()
        "projects" -> ProjectsScreen()
        "first-look" -> FirstLookScreen()
        "work" -> WorkScreen()
        "people" -> PeopleScreen()
        "team-plan" -> TeamPlanScreen()
        else -> BlocksScreen(name)
    }
}

private class MoreItem(val title: String, val icon: String, val destination: @Composable () -> Unit)

@Composable
private fun MoreMenu(title: String, items: List<MoreItem?>, footer: String? = null) {
    val shown = items.filterNotNull()
    if (shown.isEmpty()) return
    val nav = LocalNav.current
    Panel(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AxType.footnoteBold, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp))
            CardList(shown) { item ->
                MenuRow(moreIcon(item.icon), item.title, modifier = Modifier.padding(horizontal = 4.dp)) {
                    nav.push("more-" + item.title, item.destination)
                }
            }
            if (footer != null) {
                Text(footer, style = AxType.caption, color = Theme.ax.secondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp))
            }
        }
    }
}
