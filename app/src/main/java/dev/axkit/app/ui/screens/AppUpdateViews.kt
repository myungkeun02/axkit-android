package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.axkit.app.AppUpdater
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.AppUpdates
import kotlinx.coroutines.launch

/**
 * §32: reads `views/app-updates` when the app opens and each time it comes back to the front (the updater itself only
 * asks the Mac on start and once a day). After the owner turns on "이 출처 허용" and comes back, the install goes on.
 */
@Composable
fun AppUpdateChecks() {
    val model = LocalModel.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled = model.capabilities.has("app-updates")
    LaunchedEffect(enabled, model.basicsLoaded) {
        if (model.basicsLoaded) model.client?.let { model.updater.check(it, enabled) }
    }
    DisposableEffect(lifecycle, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val client = model.client ?: return@LifecycleEventObserver
            model.scope.launch { model.updater.check(client, enabled) }
            if (model.updater.phase == AppUpdater.Phase.NeedsPermission && model.updater.canInstall()) model.updater.update(client)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** Top of the 홈 tab: "새 버전이 있어요 · 바꾸기" until 나중에 (back next start). Stays while it downloads or installs. */
@Composable
fun AppUpdateBanner() {
    val model = LocalModel.current
    val updater = model.updater
    val build = updater.available ?: return
    // Working on it (downloading, checking, installing): stays up and can't be put away; otherwise 나중에 hides it.
    val working = updater.phase is AppUpdater.Phase.Downloading || updater.phase == AppUpdater.Phase.Verifying || updater.phase == AppUpdater.Phase.Installing
    if (updater.bannerHidden && !working) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(Theme.ax.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(22.dp), tint = Theme.ax.accent)
            Text(AppUpdates.title(build), style = AxType.headline, color = Theme.ax.label, modifier = Modifier.weight(1f))
            if (!working) Text("나중에", style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.clickable { updater.bannerHidden = true }.padding(4.dp))
        }
        UpdateBody(compact = true)
    }
}

/** 설정 화면: this app's version, and the update when there is one. */
@Composable
fun AppUpdateSettingsPanel() {
    val model = LocalModel.current
    val updater = model.updater
    val build = updater.available
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(26.dp), tint = if (build != null) Theme.ax.accent else Theme.ax.secondary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (build != null) AppUpdates.title(build) else "앱 버전", style = AxType.headline, color = Theme.ax.label)
                    Text("지금 앱 ${dev.axkit.app.BuildConfig.VERSION_NAME} (${dev.axkit.app.BuildConfig.VERSION_CODE})" +
                        if (build == null && model.capabilities.has("app-updates")) " · 최신이에요" else "", style = AxType.footnote, color = Theme.ax.secondary)
                }
            }
            if (build != null) UpdateBody(compact = false)
        }
    }
}

@Composable
private fun UpdateBody(compact: Boolean) {
    val model = LocalModel.current
    val context = LocalContext.current
    val updater = model.updater
    val build = updater.available ?: return
    when (val phase = updater.phase) {
        AppUpdater.Phase.Idle -> {
            if (!compact) Text(
                "맥이 만든 새 버전이에요. 바꿔도 대화와 연결은 그대로예요." + (AppUpdates.size(build.size)?.let { " 크기 $it." } ?: ""),
                style = AxType.footnote, color = Theme.ax.secondary,
            )
            ActionButton("바꾸기", kind = ActionKind.Primary, fullWidth = !compact, small = compact) { model.client?.let { updater.update(it) } }
        }
        is AppUpdater.Phase.Downloading -> {
            Text("받는 중" + if (phase.progress >= 0) " · ${(phase.progress * 100).toInt()}%" else "…", style = AxType.footnote, color = Theme.ax.secondary)
            if (phase.progress >= 0) LinearProgressIndicator(progress = { phase.progress }, Modifier.fillMaxWidth(), color = Theme.ax.accent)
            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Theme.ax.accent)
        }
        AppUpdater.Phase.Verifying -> Text("받은 파일을 확인하는 중…", style = AxType.footnote, color = Theme.ax.secondary)
        AppUpdater.Phase.Installing -> Text("설치 화면을 여는 중… '업데이트'를 눌러 주세요.", style = AxType.footnote, color = Theme.ax.secondary)
        AppUpdater.Phase.NeedsPermission -> {
            Text(AppUpdates.NEEDS_PERMISSION, style = AxType.footnote, color = Theme.ax.label)
            ActionButton("설정 열기", kind = ActionKind.Primary, fullWidth = !compact, small = compact) { context.startActivity(updater.permissionIntent()) }
        }
        is AppUpdater.Phase.Failed -> {
            Text(phase.why, style = AxType.footnote, color = SystemColor.red)
            ActionButton("다시 해 보기", fullWidth = !compact, small = compact) { updater.dismissProblem(); model.client?.let { updater.update(it) } }
        }
    }
}
