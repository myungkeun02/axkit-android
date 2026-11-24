package dev.axkit.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.axkit.app.Connection
import dev.axkit.app.LocalModel
import dev.axkit.app.Tab
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.NavHost
import dev.axkit.app.ui.design.NavStack
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.ToastHost

/** One back stack per tab, like iOS TabView + NavigationStack. */
object TabStacks {
    val stacks = Tab.entries.associateWith { NavStack() }
}

@Composable
fun RootView() {
    val model = LocalModel.current
    Box(Modifier.fillMaxSize().background(Theme.ax.page)) {
        if (model.isPaired) {
            // Android 13+: ask once for notifications after pairing (iOS asks when the Mac can push).
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
            val holder = rememberSaveableStateHolder()
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    holder.SaveableStateProvider(model.tab.name) {
                        val nav = TabStacks.stacks.getValue(model.tab)
                        NavHost(nav) {
                            when (model.tab) {
                                Tab.Home -> HomeView()
                                Tab.Approvals -> ApprovalsView()
                                Tab.Docs -> DocsView()
                                Tab.Calendar -> CalendarScreen()
                                Tab.More -> MoreView()
                            }
                        }
                    }
                }
            }
            // A full-screen page (the browser hand-over) hides the bar, like iOS .toolbar(.hidden, for: .tabBar).
            if (!model.hideTabBar) TabBar(Modifier.align(Alignment.BottomCenter))
        } else {
            val nav = remember { NavStack() }
            NavHost(nav) { PairView(isOnboarding = true) }
        }
        Box(Modifier.statusBarsPadding()) { ToastHost(model.toast) { model.toast = null } }
        Box(Modifier.statusBarsPadding()) { UrgentBanner() }
        if (model.isPaired) {
            AppUpdateChecks()
            // Only over the chat (설정 has the same panel); never on top of an urgent banner.
            if (model.banner == null && model.tab == Tab.Home) Box(Modifier.statusBarsPadding()) { AppUpdateBanner() }
        }
    }
}

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

private val tabItems = listOf(
    TabItem(Tab.Home, "홈", Icons.Rounded.Forum),
    TabItem(Tab.Approvals, "결재", Icons.Rounded.Verified),
    TabItem(Tab.Docs, "문서", Icons.Rounded.Description),
    TabItem(Tab.Calendar, "캘린더", Icons.Rounded.CalendarMonth),
    TabItem(Tab.More, "더보기", Icons.Rounded.MoreHoriz),
)

/** The floating rounded tab bar of iOS 26: five items, the chosen one on a soft grey pill, a red number on 결재. */
@Composable
fun TabBar(modifier: Modifier = Modifier) {
    val model = LocalModel.current
    val count = model.openCardCount
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.16f))
                .clip(RoundedCornerShape(32.dp))
                .background(Theme.ax.card.copy(alpha = 0.97f))
                .border(0.5.dp, Theme.ax.separator.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            for (item in tabItems) {
                val selected = model.tab == item.tab
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (selected) Theme.ax.fill else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (model.tab == item.tab) TabStacks.stacks.getValue(item.tab).popToRoot() else model.tab = item.tab
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(item.icon, item.label, Modifier.size(26.dp), tint = if (selected) Theme.ax.accent else Theme.ax.label)
                            if (item.tab == Tab.Approvals && count > 0) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).widthIn(min = 18.dp).height(18.dp)
                                        .clip(CircleShape).background(SystemColor.red).padding(horizontal = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("$count", style = AxType.caption2, color = Color.White) }
                            }
                        }
                        Text(item.label, style = AxType.caption2, color = if (selected) Theme.ax.accent else Theme.ax.label)
                    }
                }
            }
        }
    }
}

/** The global stop switch is on: say so everywhere, plainly. */
@Composable
fun StopBanner() {
    val model = LocalModel.current
    if (!model.stopped) return
    Row(
        Modifier.fillMaxWidth().background(SystemColor.red).padding(10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PanTool, null, Modifier.size(14.dp), tint = Color.White)
        Text("  전체 정지 중이에요. 보내기·결제·삭제는 맥에서 막혀 있어요.", style = AxType.footnoteBold, color = Color.White)
    }
}

/** Shown on 홈 only when the Mac can't be reached; tapping opens 설정 및 연결. */
@Composable
fun ConnectionBanner() {
    val model = LocalModel.current
    var showSettings by remember { mutableStateOf(false) }
    val c = model.connection
    if (c == Connection.Live || c == Connection.Connecting) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(Theme.ax.card).clickable { showSettings = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.color))
        Text("  ${c.label}", style = AxType.footnoteBold, color = Theme.ax.label, modifier = Modifier.weight(1f))
        Text("설정 보기", style = AxType.footnoteBold, color = Theme.ax.accent)
    }
    if (showSettings) dev.axkit.app.ui.design.AxSheet(onDismiss = { showSettings = false }) {
        SettingsScreen(inSheet = true, onClose = { showSettings = false })
    }
}


/** An urgent notice or a job's result for another room while the app is open: tap to go there; it leaves after 8 s. */
@Composable
private fun UrgentBanner() {
    val model = LocalModel.current
    val banner = model.banner ?: return
    LaunchedEffect(banner) { kotlinx.coroutines.delay(8000); if (model.banner == banner) model.banner = null }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (banner.urgent) dev.axkit.app.ui.design.SystemColor.red else Theme.ax.accent)
            .clickable { model.banner = null; model.showRoom(banner.room) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text((if (banner.urgent) "⚠️ " else "") + banner.title, style = AxType.footnoteBold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
            Text(dev.axkit.core.ChatText.preview(banner.text, 120), style = AxType.subheadline, color = androidx.compose.ui.graphics.Color.White, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text("보기", style = AxType.footnoteBold, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(start = 8.dp))
    }
}
