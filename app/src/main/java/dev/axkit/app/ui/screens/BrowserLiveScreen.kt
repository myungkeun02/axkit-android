package dev.axkit.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.ApiError
import dev.axkit.core.BrowserFrame
import dev.axkit.core.BrowserLiveState
import dev.axkit.core.BrowserLiveStream
import dev.axkit.core.BrowserLiveWords
import dev.axkit.core.Format
import dev.axkit.core.ProtocolJson
import dev.axkit.core.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import kotlin.math.roundToInt

private sealed interface LivePhase {
    object Loading : LivePhase
    data class Nothing(val words: String) : LivePhase
    object Live : LivePhase
    object Finishing : LivePhase
}

/**
 * 브라우저 화면 (§22): the browser errand stopped at a login, captcha, consent or final button. The owner sees the page,
 * taps and types on it, then presses 다 했어요 so the assistant carries on. Same layout as iOS BrowserLiveScreen.
 */
@Composable
fun BrowserLiveScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<LivePhase>(LivePhase.Loading) }
    var state by remember { mutableStateOf<BrowserLiveState?>(null) }
    var frame by remember { mutableStateOf<BrowserFrame?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastTap by remember { mutableStateOf<Offset?>(null) }
    var showTyping by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    val client = model.client

    // The whole height is the page: no floating tab bar here.
    DisposableEffect(Unit) { model.hideTabBar = true; onDispose { model.hideTabBar = false } }

    fun applyState(s: BrowserLiveState) {
        state = s.copy(idleClosesAt = s.idleClosesAt ?: state?.idleClosesAt)
        if (!s.live && phase == LivePhase.Live) { streaming = false; phase = LivePhase.Nothing(BrowserLiveWords.closed(s.closed) ?: "브라우저 화면이 닫혔어요.") }
    }

    fun act(action: String, body: JsonElement = JsonObject(emptyMap())) {
        val c = client ?: return
        if (phase != LivePhase.Live) return
        scope.launch {
            try {
                val raw = c.postRaw("views/browser-live/$action", body)
                val data = (ProtocolJson.parseToJsonElement(raw) as? JsonObject)?.get("data") as? JsonObject
                if (data?.get("accepted")?.jsonPrimitive?.content == "false") {
                    // Too fast (more than 8 a second): try once more a moment later.
                    delay(maxOf(data["retryAfterMs"]?.jsonPrimitive?.intOrNull ?: 250, 100).toLong())
                    runCatching { c.postRaw("views/browser-live/$action", body) }
                }
            } catch (e: Exception) {
                model.toast = e.friendly
                if (e is ApiError.Refused) runCatching { c.view(BrowserLiveState.serializer(), "browser-live") }.getOrNull()?.let { if (!it.live) applyState(it) }
            }
        }
    }

    LaunchedEffect(client) {
        val c = client ?: return@LaunchedEffect
        try {
            var s = c.view(BrowserLiveState.serializer(), "browser-live")
            if (!s.live) {
                if (s.pending == null) {
                    phase = LivePhase.Nothing(BrowserLiveWords.closed(s.closed) ?: "브라우저 심부름이 로그인이나 확인 단계에서 멈추면 여기서 그 페이지를 직접 볼 수 있어요.")
                    return@LaunchedEffect
                }
                s = c.viewAction(BrowserLiveState.serializer(), "browser-live", "start", JsonObject(emptyMap()))
            }
            state = s; phase = LivePhase.Live; streaming = true
        } catch (e: Exception) { phase = LivePhase.Nothing(e.friendly) }
    }

    // The stream lives while the screen is up and the app is in front (3 streams per device).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) streaming = false
            if (e == Lifecycle.Event.ON_START && phase == LivePhase.Live) streaming = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(client, streaming) {
        val c = client
        val stream = if (c != null && streaming) BrowserLiveStream(c) else null
        stream?.start(
            scope,
            onState = { applyState(it) },
            onFrame = { f ->
                frame = f
                image = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(f.jpeg, 0, f.jpeg.size)?.asImageBitmap() }
                lastTap = null
            },
        )
        onDispose { stream?.stop() }
    }

    fun finish() {
        val c = client ?: return
        phase = LivePhase.Finishing; streaming = false
        scope.launch {
            try {
                val s = c.viewAction(BrowserLiveState.serializer(), "browser-live", "done", JsonObject(emptyMap()))
                nav.pop()
                model.toast = BrowserLiveWords.doneWords(s)
                // Back to the chat the errand came from; when it carries on in Discord, just say so and stay.
                BrowserLiveWords.returnRoom(s)?.let { model.showRoom(it) }
            } catch (e: Exception) {
                phase = LivePhase.Live; streaming = true
                model.toast = e.friendly
            }
        }
    }

    val live = phase == LivePhase.Live || phase == LivePhase.Finishing
    Screen("브라우저 화면", trailing = {
        if (live) BarButton("다 했어요", bold = true, enabled = phase == LivePhase.Live) { finish() }
    }) {
        when (val p = phase) {
            LivePhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Theme.ax.secondary)
                    Text("브라우저 화면을 여는 중…", style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(top = 10.dp))
                }
            }
            is LivePhase.Nothing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyStateView(Icons.Rounded.Explore, "지금 넘겨받을 화면이 없어요", p.words)
            }
            else -> Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // Header: lock + host, until when it stays open, why it was handed over.
                Column(Modifier.fillMaxWidth().background(Theme.ax.card).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(11.dp), tint = Theme.ax.secondary)
                        val host = (frame?.url ?: state?.url)?.let { runCatching { URI(it).host }.getOrNull() } ?: ""
                        Text(host, style = AxType.footnoteBold, color = Theme.ax.label, maxLines = 1, modifier = Modifier.weight(1f))
                        state?.idleClosesAt?.let { Text("${Format.clock(it)}까지 열려 있어요", style = AxType.caption2, color = Theme.ax.tertiary) }
                    }
                    Text(BrowserLiveWords.reason(state?.reason), style = AxType.caption, color = Theme.ax.secondary)
                }
                // The page, aspect-fit. Tap = tap there (JPEG pixels), drag up/down = scroll.
                var shown by remember { mutableStateOf(IntSize.Zero) }
                var dragged by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Theme.ax.page).onSizeChanged { shown = it }
                        .semantics { contentDescription = frame?.title ?: "브라우저 화면" }
                        .pointerInput(frame?.width, frame?.height, shown) {
                            detectTapGestures { pt ->
                                val f = frame ?: return@detectTapGestures
                                val px = BrowserLiveWords.pixel(pt.x, pt.y, shown.width.toFloat(), shown.height.toFloat(), f.width, f.height) ?: return@detectTapGestures
                                lastTap = pt
                                act("tap", buildJsonObject { put("x", JsonPrimitive(px.first)); put("y", JsonPrimitive(px.second)) })
                            }
                        }
                        .pointerInput(frame?.width, frame?.height, shown) {
                            detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = {
                                val f = frame ?: return@detectVerticalDragGestures
                                val scale = minOf(shown.width.toFloat() / f.width, shown.height.toFloat() / f.height).coerceAtLeast(0.01f)
                                if (kotlin.math.abs(dragged) >= 24) act("scroll", buildJsonObject { put("dy", JsonPrimitive((-dragged / scale * 1.3f).roundToInt().coerceIn(-4000, 4000))) })
                            }) { _, amount -> dragged += amount }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val img = image
                    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else CircularProgressIndicator(color = Theme.ax.secondary)
                    lastTap?.let { tap ->
                        val accent = Theme.ax.accent
                        Canvas(Modifier.fillMaxSize()) { drawCircle(accent, radius = 17.dp.toPx(), center = tap, style = Stroke(2.dp.toPx())) }
                    }
                    if (phase == LivePhase.Finishing) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Theme.ax.card).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Theme.ax.secondary)
                                Text("  비서에게 넘기는 중…", style = AxType.subheadline, color = Theme.ax.label)
                            }
                        }
                    }
                }
                // Controls: back · type · enter · delete · next field · up · down.
                Row(Modifier.fillMaxWidth().background(Theme.ax.card).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val on = phase == LivePhase.Live
                    LiveControl(Icons.AutoMirrored.Rounded.ArrowBackIos, "뒤로", on, Modifier.weight(1f)) { act("back") }
                    LiveControl(Icons.Rounded.Keyboard, "글자 쓰기", on, Modifier.weight(1f)) { showTyping = true }
                    LiveControl(Icons.AutoMirrored.Rounded.KeyboardReturn, "엔터", on, Modifier.weight(1f)) { act("key", buildJsonObject { put("name", JsonPrimitive("Enter")) }) }
                    LiveControl(Icons.Rounded.Backspace, "지우기", on, Modifier.weight(1f)) { act("key", buildJsonObject { put("name", JsonPrimitive("Backspace")) }) }
                    LiveControl(Icons.AutoMirrored.Rounded.KeyboardTab, "다음 칸", on, Modifier.weight(1f)) { act("key", buildJsonObject { put("name", JsonPrimitive("Tab")) }) }
                    LiveControl(Icons.Rounded.ArrowUpward, "위로", on, Modifier.weight(1f)) { act("scroll", buildJsonObject { put("dy", JsonPrimitive(-700)) }) }
                    LiveControl(Icons.Rounded.ArrowDownward, "아래로", on, Modifier.weight(1f)) { act("scroll", buildJsonObject { put("dy", JsonPrimitive(700)) }) }
                }
            }
        }
    }

    if (showTyping) TypingSheet(onDismiss = { showTyping = false }) { text -> showTyping = false; act("type", buildJsonObject { put("text", JsonPrimitive(text)) }) }
}

@Composable
private fun LiveControl(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 40.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(10.dp))
            .background(Theme.ax.accent.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = Theme.ax.accent) }
}

/** 글자 쓰기: the whole text at once into the field the owner tapped (not letter by letter). */
@Composable
private fun TypingSheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var typing by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AxSheet(onDismiss) {
        SheetBar("글자 쓰기", leading = { BarButton("취소") { onDismiss() } }, trailing = { BarButton("보내기", bold = true, enabled = typing.isNotEmpty()) { onSend(typing) } })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = typing, onValueChange = { typing = it.take(1000) }, maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).clip(RoundedCornerShape(10.dp)).background(Theme.ax.card)
                    .border(0.5.dp, Theme.ax.separator, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (typing.isEmpty()) Text("먼저 화면에서 칸을 누르고, 여기에 쓰세요", style = AxType.body, color = Theme.ax.tertiary)
                    inner()
                },
            )
            Text("다 쓴 뒤 한 번에 보내요. 쓴 글자는 그 페이지로만 가고, 맥의 기록이나 대화에는 남지 않아요.", style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
