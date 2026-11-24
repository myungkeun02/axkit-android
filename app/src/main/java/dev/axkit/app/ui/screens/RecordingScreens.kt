package dev.axkit.app.ui.screens

import androidx.compose.foundation.clickable
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.axkit.app.Connection
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loaded
import dev.axkit.app.ui.design.safeTap
import dev.axkit.core.Format
import dev.axkit.core.RecordingsView
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Red bar under every screen title while recording: elapsed time and one button to stop. */
@Composable
fun RecordingBar() {
    val model = LocalModel.current
    val started = model.recorder.startedAt ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(started) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    Row(
        Modifier.fillMaxWidth().background(Theme.ax.card).semantics(mergeDescendants = true) { contentDescription = "녹음 중" }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SystemColor.red))
        Text(
            "녹음 중 ${recordingClock((now - started).coerceAtLeast(0) / 1000.0)}",
            style = AxType.subheadlineBold.copy(fontFeatureSettings = "tnum"), color = Theme.ax.label,
        )
        Spacer(Modifier.weight(1f))
        ActionButton("끝내기", kind = ActionKind.Danger, fullWidth = false, small = true) { model.stopRecording() }
    }
}

/** 05:07, or 1:02:03 past an hour. */
fun recordingClock(seconds: Double): String {
    val s = seconds.toInt()
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60)
}

private const val MIC_PROBLEM = "마이크를 쓸 수 없어요. 설정 › ax-kit에서 마이크를 켜 주세요."

private fun Context.noticeSeen(): Boolean = getSharedPreferences("axkit", Context.MODE_PRIVATE).getBoolean("recordingNoticeSeen", false)
private fun Context.markNoticeSeen() = getSharedPreferences("axkit", Context.MODE_PRIVATE).edit().putBoolean("recordingNoticeSeen", true).apply()

/**
 * Starts (or stops) a recording. The first time it says plainly what is and isn't OK to record,
 * then asks for the microphone.
 */
@Composable
fun RecordButton(label: String, kind: ActionKind, modifier: Modifier = Modifier) {
    val model = LocalModel.current
    val context = LocalContext.current
    var showNotice by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) model.startRecording() else model.toast = MIC_PROBLEM
    }
    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.startRecording()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    ActionButton(label, modifier, kind = kind) {
        when {
            model.recorder.isRecording -> model.stopRecording()
            context.noticeSeen() -> start()
            else -> showNotice = true
        }
    }
    if (showNotice) {
        AlertDialog(
            onDismissRequest = { showNotice = false },
            containerColor = Theme.ax.card,
            title = { Text("녹음하기 전에", style = AxType.headline, color = Theme.ax.label) },
            text = {
                Text(
                    "내가 함께 있는 대화만 녹음할 수 있어요. 내가 빠진 다른 사람들의 대화를 몰래 녹음하면 안 돼요.\n녹음은 맥에서 글로 정리되고, 휴대폰에서는 보낸 뒤 지워져요.",
                    style = AxType.subheadline, color = Theme.ax.secondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotice = false; context.markNoticeSeen(); start() }) {
                    Text("알겠어요, 녹음할게요", style = AxType.bodyBold, color = Theme.ax.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotice = false }) { Text("취소", style = AxType.body, color = Theme.ax.accent) }
            },
        )
    }
}

private fun recordingTitle(date: Instant?): String {
    if (date == null) return "녹음"
    return "${Format.relative(date)} 녹음 · ${Format.clock(date)}"
}

/** 녹음과 회의록: start a recording, see what is still on its way, and open the documents made from them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<RecordingsView>>(Loadable.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        val client = model.client ?: return
        if (!model.capabilities.hasView("recordings")) return
        try {
            state = Loadable.Loaded(client.view(RecordingsView.serializer(), "recordings"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (state.loaded == null) state = Loadable.Failed(e.friendly)
        }
    }

    // Processing takes a while; look again every few seconds while the screen is open.
    LaunchedEffect(Unit) {
        while (true) { load(); delay(4000) }
    }

    Screen("녹음해서 회의록 만들기", large = true) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Page {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "녹음하면 맥이 글로 받아 적고, 회의록 문서로 정리해요. 할 만한 일이 보이면 결재로 제안해요.",
                            style = AxType.subheadline, color = Theme.ax.secondary,
                        )
                        val recording = model.recorder.isRecording
                        RecordButton(if (recording) "녹음 끝내기" else "녹음 시작", if (recording) ActionKind.Danger else ActionKind.Primary)
                    }
                }
                if (model.pendingRecordings.isNotEmpty()) {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("맥으로 보내는 중")
                            CardList(model.pendingRecordings) { r ->
                                val problem = r.problem
                                if (problem != null) {
                                    // Refused by the Mac: the audio stays on this phone until the owner sends it again or deletes it.
                                    var confirm by remember { mutableStateOf(false) }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CardRow(recordingTitle(Instant.ofEpochMilli(r.startedAt)), "맥이 받지 않았어요 · $problem") {}
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("다시 보내기", style = AxType.subheadlineBold, color = Theme.ax.accent,
                                                modifier = Modifier.clickable { model.resendRecording(r.id) }.padding(vertical = 4.dp))
                                            Text("지우기", style = AxType.subheadlineBold, color = dev.axkit.app.ui.design.SystemColor.red,
                                                modifier = Modifier.clickable { confirm = true }.padding(vertical = 4.dp))
                                        }
                                    }
                                    if (confirm) androidx.compose.material3.AlertDialog(
                                        onDismissRequest = { confirm = false },
                                        title = { Text("이 녹음을 지울까요?") },
                                        text = { Text("휴대폰에서 녹음 파일이 지워지고 되살릴 수 없어요.") },
                                        confirmButton = { androidx.compose.material3.TextButton({ confirm = false; model.deleteRecording(r.id) }) { Text("지우기", color = dev.axkit.app.ui.design.SystemColor.red) } },
                                        dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("취소") } },
                                    )
                                } else CardRow(
                                    recordingTitle(Instant.ofEpochMilli(r.startedAt)),
                                    "${Format.duration(r.durationSec)} · ${(r.progress * 100).toInt()}% 보냄",
                                ) {
                                    LinearProgressIndicator(
                                        progress = { r.progress.toFloat() }, modifier = Modifier.width(60.dp),
                                        color = Theme.ax.accent, trackColor = Theme.ax.fill, drawStopIndicator = {},
                                    )
                                }
                            }
                            if (model.connection != Connection.Live) {
                                Text("맥이 연결되면 이어서 보내요.", style = AxType.footnote, color = Theme.ax.secondary)
                            }
                        }
                    }
                }
                if (model.capabilities.hasView("recordings")) {
                    val data = state.loaded
                    val current = state
                    if (data != null && data.recordings.isNotEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader("회의록")
                                CardList(data.recordings) { item -> RecordingRow(item) }
                            }
                        }
                    } else if (current is Loadable.Failed) {
                        Text(current.message, style = AxType.footnote, color = Theme.ax.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(item: RecordingsView.Item) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val words = when (item.status) {
        "receiving" -> "받는 중"
        "transcribing" -> "글로 받아 적는 중"
        "summarizing" -> "회의록으로 정리하는 중"
        "failed" -> "정리하지 못했어요"
        else -> if (item.driveUrl != null) "정리 끝 · Drive에 저장됨" else "정리 끝"
    }
    val detail = listOfNotNull(item.statusText ?: words, item.durationSec?.let { Format.duration(it) }).joinToString(" · ")
    val title = item.title ?: recordingTitle(item.startedAt)
    val doc = item.documentId
    when {
        doc != null && item.status == "done" ->
            CardRow(title, detail, modifier = Modifier.safeTap { nav.push("doc-$doc") { DocumentScreen(doc) } }) { Chevron() }
        item.status == "done" && item.driveUrl != null -> {
            // Only opened when the owner taps it, in the system browser.
            val url = item.driveUrl!!
            CardRow(title, detail, modifier = Modifier.safeTap {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Drive에서 열기", Modifier.size(18.dp), tint = Theme.ax.tertiary) }
        }
        else -> CardRow(title, detail, titleColor = if (item.status == "failed") SystemColor.red else Theme.ax.label) {
            if (item.status != "failed" && item.status != "done") {
                CircularProgressIndicator(Modifier.size(18.dp), color = Theme.ax.secondary, strokeWidth = 2.dp)
            }
        }
    }
}
