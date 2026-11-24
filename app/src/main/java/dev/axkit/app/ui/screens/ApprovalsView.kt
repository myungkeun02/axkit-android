package dev.axkit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardPanel
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.GlassButton
import dev.axkit.app.ui.design.NavHost
import dev.axkit.app.ui.design.NavStack
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.Theme
import kotlinx.coroutines.launch

/**
 * 결재: everything the assistant or a bot is asking the owner — "할까요?" — answered with one tap (네/아니요/…),
 * or with "다른 지시 쓰기" for anything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsView() {
    val model = LocalModel.current
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showAuto by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Reading the timeline subscribes to it. openCards() already hides older copies of the same question.
    val timeline = model.timeline
    val open = timeline.openCards()
    val done = timeline.finishedCards()

    Screen(
        "결재", large = true,
        trailing = if (model.capabilities.hasView("autonomy")) {
            { GlassButton(label = "자동 승인") { showAuto = true } }
        } else null,
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; try { model.refreshBasics() } finally { refreshing = false } } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Page {
                StopBanner()
                if (open.isEmpty()) {
                    EmptyStateView(
                        SfIcons.of("checkmark.seal"), "지금은 결재할 게 없어요",
                        "비서나 봇이 \"이거 해도 될까요?\" 하고 물어보면 여기에 올라와요.",
                        modifier = Modifier.padding(top = 40.dp),
                    )
                } else {
                    for (card in open) key(card.cardId) { CardPanel(card) }
                }
                if (done.isNotEmpty()) {
                    Column(Modifier.padding(top = 10.dp)) {
                        // iOS DisclosureGroup: label on the left, accent chevron on the right that turns down when open.
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { showDone = !showDone }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("지난 결재 ${done.size}개", style = AxType.headline, color = Theme.ax.label)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (showDone) "접기" else "펼치기",
                                Modifier.size(22.dp).rotate(if (showDone) 90f else 0f), tint = Theme.ax.accent,
                            )
                        }
                        AnimatedVisibility(showDone) {
                            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (card in done) key(card.cardId) { CardPanel(card, compact = true) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuto) AxSheet(onDismiss = { showAuto = false }) {
        val nav = remember { NavStack() }
        NavHost(nav) { AutonomyScreen(onClose = { showAuto = false }) }
    }
}
