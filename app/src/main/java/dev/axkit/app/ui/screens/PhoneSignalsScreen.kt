package dev.axkit.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.axkit.app.LocalModel
import dev.axkit.app.signals.PhoneSignals
import dev.axkit.app.signals.StepsSignals
import dev.axkit.app.signals.listenerComponent
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.AxSwitch
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.Format
import dev.axkit.core.SignalKind
import dev.axkit.core.Signals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** 설정 › 휴대폰 신호 entry: one panel that opens the screen. */
@Composable
fun PhoneSignalsSettingsPanel() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val on = SignalKind.entries.filter { PhoneSignals.isOn(context, it) }
    Panel {
        Row(
            Modifier.fillMaxWidth().clickable { nav.push("signals") { PhoneSignalsScreen() } },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Sensors, null, Modifier.size(26.dp), tint = Theme.ax.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("휴대폰 신호", style = AxType.headline, color = Theme.ax.label)
                Text(
                    if (on.isEmpty()) "문자·카톡·카드 알림과 위치를 맥 비서에게 보내요. 지금은 꺼져 있어요."
                    else "보내는 중: " + on.joinToString(", ") { it.title },
                    style = AxType.footnote, color = Theme.ax.secondary,
                )
            }
            Chevron()
        }
    }
}

/** Pushed inside the settings sheet (its own small bar) or on a stack (the normal top bar). */
@Composable
private fun SubScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    if (LocalSettingsInSheet.current) {
        Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
            SheetBar(title, leading = { BarButton("‹ 뒤로") { nav.pop() } })
            content()
        }
    } else Screen(title, content = content)
}

/** 휴대폰 신호: what the phone sends the Mac, each with its permission, a switch and when it last went. */
@Composable
fun PhoneSignalsScreen() {
    val context = LocalContext.current
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    // Bumped on return from the system settings and after every switch, so everything below reads again.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var queued by remember { mutableIntStateOf(0) }
    var stepsGranted by remember { mutableStateOf(false) }
    val stepsAvailable = remember { StepsSignals.available(context) }
    LaunchedEffect(tick) {
        queued = PhoneSignals.queue(context).count()
        if (stepsAvailable) stepsGranted = StepsSignals.granted(context)
    }

    val notifAccess = remember(tick) { PhoneSignals.hasNotificationAccess(context) }
    val location = remember(tick) { PhoneSignals.hasLocation(context) }
    val backgroundLocation = remember(tick) { PhoneSignals.hasBackgroundLocation(context) }
    val batteryFree = remember(tick) { PhoneSignals.ignoresBatteryOptimizations(context) }
    val on = remember(tick) { SignalKind.entries.associateWith { PhoneSignals.isOn(context, it) } }
    val all = remember(tick) { PhoneSignals.allApps(context) }
    val chosen = remember(tick) { PhoneSignals.chosenApps(context) }
    val problem = remember(tick) { PhoneSignals.lastProblem(context) }

    fun set(kind: SignalKind, on: Boolean) { PhoneSignals.setOn(context, kind, on); tick++ }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; PhoneSignals.refresh(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        tick++
        if (result.values.any { it }) {
            PhoneSignals.refresh(context)
            if (!PhoneSignals.hasBackgroundLocation(context)) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    val stepsLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { tick++ }

    fun openNotificationAccess() {
        val intent = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun openAppDetails() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    fun askLocation() {
        if (!location) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else if (!backgroundLocation) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    SubScreen("휴대폰 신호") {
        Page {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("휴대폰이 보는 것을 맥 비서에게 보내요", style = AxType.headline, color = Theme.ax.label)
                    Text(
                        "카드·은행 알림은 가계부에, 인증번호는 브라우저 자동 로그인에, 택배·예약 문자는 할 일에, 위치는 점심·이동 시간·날씨에 써요.",
                        style = AxType.subheadline, color = Theme.ax.secondary,
                    )
                    Text("이걸 켜 두면 MacroDroid는 이제 필요 없어요.", style = AxType.subheadlineBold, color = Theme.ax.accent)
                }
            }

            // 알림 접근: 앱 알림 + 문자
            Panel {
                CardHeader("알림 읽기", "문자 권한 없이, 메시지 앱의 알림으로 문자도 읽어요.")
                Spacer(Modifier.height(6.dp))
                PermissionRow("알림 접근", "휴대폰에 뜨는 알림을 읽는 권한이에요.", notifAccess, "켜기") { openNotificationAccess() }
                if (!notifAccess) Text(
                    "'제한된 설정'이라고 나오면: 앱 정보 › 오른쪽 위 ⋮ › '제한된 설정 허용'을 누른 뒤 다시 켜 주세요.",
                    style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(bottom = 6.dp),
                )
                if (!notifAccess) ActionButton("앱 정보 열기", small = true, fullWidth = false) { openAppDetails() }
                Hairline()
                KindRow(SignalKind.Sms, "결제 문자는 가계부, 택배·예약은 할 일, 인증번호는 자동 로그인에 써요.", on.getValue(SignalKind.Sms), tick) { v ->
                    set(SignalKind.Sms, v); if (v && !notifAccess) openNotificationAccess()
                }
                Hairline()
                KindRow(SignalKind.Notification, "카톡, 카드·은행·페이 앱 알림이에요. 결제 알림은 가계부에 적어요.", on.getValue(SignalKind.Notification), tick) { v ->
                    set(SignalKind.Notification, v); if (v && !notifAccess) openNotificationAccess()
                }
                Hairline()
                Row(
                    Modifier.fillMaxWidth().clickable { nav.push("signals-apps") { SignalAppsScreen() } }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("보낼 앱", style = AxType.body, color = Theme.ax.label)
                        Text(
                            when { all -> "모든 앱"; chosen == null -> "추천: 메시지, 카톡, 카드·은행·페이 앱"; else -> "고른 앱 ${chosen.size}개" },
                            style = AxType.footnote, color = Theme.ax.secondary,
                        )
                    }
                    Chevron()
                }
            }

            // 위치
            Panel {
                KindRow(SignalKind.Location, "점심 추천, 다음 약속까지 걸리는 시간, 아침 브리핑 날씨, 집·회사 알아 두기에 써요. 움직일 때 30분쯤마다 보내요.", on.getValue(SignalKind.Location), tick) { v ->
                    set(SignalKind.Location, v); if (v) askLocation()
                }
                Hairline()
                PermissionRow(
                    "위치 항상 허용",
                    if (!location) "앱을 닫아도 위치를 알 수 있게 '항상 허용'을 골라 주세요."
                    else if (!backgroundLocation) "지금은 '앱 사용 중에만'이에요. 화면이 꺼져도 보내려면 '항상 허용'으로 바꿔 주세요."
                    else "앱을 닫아도 위치를 알 수 있어요.",
                    backgroundLocation, if (location) "바꾸기" else "켜기",
                ) { askLocation() }
            }

            // 배터리 · 걸음 수
            Panel {
                KindRow(SignalKind.Battery, "20% 아래로 내려가면 한 번 알려요.", on.getValue(SignalKind.Battery), tick) { v -> set(SignalKind.Battery, v) }
                if (stepsAvailable) {
                    Hairline()
                    KindRow(SignalKind.Steps, "Health Connect의 하루 걸음 수를 한 시간에 한 번쯤 보내요.", on.getValue(SignalKind.Steps), tick) { v ->
                        set(SignalKind.Steps, v); if (v && !stepsGranted) stepsLauncher.launch(StepsSignals.wanted(context))
                    }
                    if (on.getValue(SignalKind.Steps) && !stepsGranted) {
                        PermissionRow("걸음 수 읽기", "Health Connect에서 걸음 수를 읽게 해 주세요.", false, "켜기") { stepsLauncher.launch(StepsSignals.wanted(context)) }
                    }
                }
            }

            // 꺼지지 않게
            Panel {
                PermissionRow(
                    "배터리 최적화 제외",
                    "화면이 꺼져도 계속 보내려면 켜 주세요. 삼성 휴대폰은 설정 › 배터리 › 백그라운드 사용 제한에서 ax-kit을 '제한 없음'으로 두면 더 좋아요.",
                    batteryFree, "켜기",
                ) {
                    @Suppress("BatteryLife")
                    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(ask) }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }

            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (queued > 0) Text("아직 못 보낸 것 ${queued}개 — 맥에 닿으면 바로 보내요.", style = AxType.footnote, color = SystemColor.orange)
                    problem?.takeIf { queued > 0 }?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
                    ActionButton(if (testing) "보내는 중…" else "지금 보내 보기", enabled = !testing && model.isPaired) {
                        testing = true; testResult = null
                        scope.launch {
                            testResult = withContext(Dispatchers.IO) { PhoneSignals.test(context) }
                            testing = false; tick++
                        }
                    }
                    Text("지금 배터리 잔량(그리고 켜 두었다면 위치·걸음 수)을 바로 보내 봐요.", style = AxType.footnote, color = Theme.ax.secondary)
                    testResult?.let { Text(it, style = AxType.subheadlineBold, color = if (it.startsWith("맥이 받았어요")) SystemColor.green else SystemColor.orange) }
                }
            }

            Text(
                "보낸 것은 연결된 맥에만 가요. 휴대폰에는 맥에 닿지 못한 것만 잠깐 남고, 닿으면 바로 지워요. 48시간이 지나도 못 보낸 것은 버려요. 위의 스위치로 종류마다 끌 수 있어요.",
                style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun KindRow(kind: SignalKind, detail: String, on: Boolean, tick: Int, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val last = remember(tick, kind) { PhoneSignals.lastSent(context, kind) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(kind.title + " 보내기", style = AxType.bodyBold, color = Theme.ax.label)
            Text(detail, style = AxType.footnote, color = Theme.ax.secondary)
            Text(
                if (last > 0) "마지막으로 보낸 때: " + Format.relative(Instant.ofEpochMilli(last)) else "아직 보낸 적 없어요",
                style = AxType.caption, color = Theme.ax.tertiary,
            )
        }
        AxSwitch(on, onChange = onChange)
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, action: String, onAsk: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = AxType.body, color = Theme.ax.label)
            Text(detail, style = AxType.footnote, color = Theme.ax.secondary)
        }
        if (granted) Text("켜짐", style = AxType.subheadlineBold, color = SystemColor.green)
        else ActionButton(action, small = true, fullWidth = false) { onAsk() }
    }
}

private data class PhoneApp(val pkg: String, val label: String, val suggested: Boolean)

/** 보낼 앱: "모든 앱", or a list (starting from the suggested ones). */
@Composable
fun SignalAppsScreen() {
    val context = LocalContext.current
    var all by remember { mutableStateOf(PhoneSignals.allApps(context)) }
    var chosen by remember { mutableStateOf(PhoneSignals.chosenApps(context)) }
    var apps by remember { mutableStateOf<List<PhoneApp>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val sms = PhoneSignals.smsApp(context)
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.distinct()
                .filter { it != context.packageName }
                .map { pkg ->
                    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    PhoneApp(pkg, label, Signals.isDefaultApp(pkg, label, sms))
                }
                .sortedWith(compareBy<PhoneApp>({ !it.suggested }, { it.label }))
        }
    }
    val current: Set<String> = chosen ?: apps.orEmpty().filter { it.suggested }.map { it.pkg }.toSet()

    SubScreen("보낼 앱") {
        Column(Modifier.fillMaxSize()) {
            Panel(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("모든 앱", style = AxType.bodyBold, color = Theme.ax.label)
                        Text("끄면 아래에서 고른 앱의 알림만 보내요. 처음엔 메시지, 카톡, 카드·은행·페이 앱이 골라져 있어요.", style = AxType.footnote, color = Theme.ax.secondary)
                    }
                    AxSwitch(all) { all = it; PhoneSignals.setAllApps(context, it) }
                }
            }
            val list = apps
            if (list == null) Text("앱 목록을 읽는 중…", style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(16.dp))
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 120.dp)) {
                items(list, key = { it.pkg }) { app ->
                    val picked = all || app.pkg in current
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !all) {
                            val next = if (app.pkg in current) current - app.pkg else current + app.pkg
                            chosen = next; PhoneSignals.setChosenApps(context, next)
                        }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(22.dp),
                            tint = if (picked) (if (all) Theme.ax.tertiary else Theme.ax.accent) else Theme.ax.tertiary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = AxType.body, color = Theme.ax.label)
                            if (app.suggested) Text("추천", style = AxType.caption, color = Theme.ax.secondary)
                        }
                    }
                    Hairline()
                }
            }
        }
    }
}
