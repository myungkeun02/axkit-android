package dev.axkit.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.axkit.app.BuildConfig
import dev.axkit.app.Connection
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.KV
import dev.axkit.app.ui.design.KVCard
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.NavHost
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.background
import dev.axkit.app.ui.design.foreground
import dev.axkit.core.Format
import kotlinx.coroutines.launch
import java.time.Instant

/** True inside the 설정 sheet, so pushed screens there skip the status-bar gap. */
internal val LocalSettingsInSheet = staticCompositionLocalOf { false }

/** 설정 및 연결: which Mac, is it reachable, pair again, unpair. */
@Composable
fun SettingsScreen(inSheet: Boolean = false, onClose: () -> Unit = {}) {
    if (inSheet) {
        // The sheet has its own stack ("다른 맥에 연결" is pushed inside it).
        CompositionLocalProvider(LocalSettingsInSheet provides true) {
            NavHost(LocalNav.current) {
                Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
                    SheetBar("설정 및 연결", trailing = { BarButton("닫기", bold = true) { onClose() } })
                    SettingsContent(inSheet = true, onClose = onClose)
                }
            }
        }
    } else {
        Screen("설정 및 연결", large = true) {
            SettingsContent(inSheet = false, onClose = onClose)
        }
    }
}

@Composable
private fun SettingsContent(inSheet: Boolean, onClose: () -> Unit) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val context = LocalContext.current
    var confirmUnpair by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // Re-check when coming back from the system settings.
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Page {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SfIcons.of("macmini"), null, Modifier.size(30.dp), tint = Theme.ax.accent)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(model.credentials?.displayHost ?: "연결된 맥 없음", style = AxType.headline, color = Theme.ax.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(model.connection.color))
                            Text(model.connection.label, style = AxType.subheadline, color = Theme.ax.secondary)
                        }
                    }
                }
                val connection = model.connection
                if (connection is Connection.Offline) {
                    Text(connection.why + " 집 밖이라면 휴대폰의 Tailscale이 켜져 있는지 확인해 주세요.", style = AxType.footnote, color = Theme.ax.secondary)
                }
                if (connection == Connection.Unauthorized) {
                    Text("맥이 이 휴대폰을 더 이상 알지 못해요. 아래 '다른 맥에 연결'로 QR을 다시 찍어 주세요.", style = AxType.footnote, color = SystemColor.red)
                }
                SettingsSpinnerButton("지금 다시 연결해 보기", busy = refreshing) {
                    refreshing = true
                    model.scope.launch {
                        model.stop()
                        model.start()
                        model.refreshBasics()
                        refreshing = false
                    }
                }
            }
        }
        if (model.isPaired) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("항상 연결해 두기", style = AxType.headline, color = Theme.ax.label)
                        Text(
                            "앱을 닫아도 맥과 연결해 두고 새 결재와 메시지를 바로 알려 드려요. 알림 줄에 작은 표시가 늘 있고 배터리를 조금 더 써요. 끄면 15분쯤마다 확인해요.",
                            style = AxType.footnote, color = Theme.ax.secondary,
                        )
                    }
                    androidx.compose.material3.Switch(checked = model.alwaysOn, onCheckedChange = { model.changeAlwaysOn(it) })
                }
            }
        }
        if (model.isPaired) AppUpdateSettingsPanel()
        if (!notificationsOn) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(26.dp), tint = SystemColor.orange)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("알림이 꺼져 있어요", style = AxType.headline, color = Theme.ax.label)
                            Text("결재가 오면 알려 드리려면 알림을 켜 주세요.", style = AxType.footnote, color = Theme.ax.secondary)
                        }
                    }
                    ActionButton("알림 켜기") {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }
        val credentials = model.credentials
        KVCard(
            "이 휴대폰",
            listOfNotNull(
                KV("이름", credentials?.deviceName ?: "-"),
                credentials?.let { KV("연결한 날", Format.relative(Instant.ofEpochMilli(it.pairedAt))) },
                KV("비서 이름", model.assistantName),
                KV("앱 버전", BuildConfig.VERSION_NAME),
            ),
        )
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("다른 맥에 연결 (QR 다시 찍기)") { nav.push("pair") { PairView(isOnboarding = false) } }
                ActionButton("연결 끊기", kind = ActionKind.Danger) { confirmUnpair = true }
                Text(
                    "연결 정보(토큰)는 이 휴대폰 안에만 잠가서 있어요. 연결을 끊으면 맥에서도 바로 지워져요.",
                    style = AxType.footnote, color = Theme.ax.secondary,
                )
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            containerColor = Theme.ax.card,
            title = { Text("이 맥과 연결을 끊을까요?", style = AxType.headline, color = Theme.ax.label) },
            text = { Text("다시 쓰려면 맥에서 QR을 새로 띄워야 해요.", style = AxType.subheadline, color = Theme.ax.secondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    model.scope.launch {
                        model.unpair()
                        if (inSheet) onClose() else nav.popToRoot()
                    }
                }) { Text("연결 끊기", style = AxType.bodyBold, color = SystemColor.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("그대로 두기", style = AxType.body, color = Theme.ax.accent) }
            },
        )
    }
}

/** The light 보조 button with a spinner at the right while it works (iOS `.action(.secondary)` + ProgressView). */
@Composable
private fun SettingsSpinnerButton(label: String, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val kind = ActionKind.Secondary
    Row(
        Modifier.fillMaxWidth().alpha(if (pressed) 0.7f else 1f).clip(RoundedCornerShape(12.dp)).background(kind.background())
            .clickable(role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AxType.bodyBold, color = kind.foreground())
        if (busy) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(18.dp), color = Theme.ax.secondary, strokeWidth = 2.dp)
        }
    }
}
