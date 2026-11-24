package dev.axkit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.axkit.app.LocalModel
import dev.axkit.app.terminal.TerminalSession
import dev.axkit.app.terminal.TerminalStore
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.DarkAx
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.LocalAx
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.SSHHost
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

/** 터미널 first screen: the phone's key (to put on each Mac) and the Macs to open. */
@Composable
fun TerminalHostsScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val store = remember { TerminalStore(context) }
    var editing by remember { mutableStateOf<SSHHost?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmNewKey by remember { mutableStateOf(false) }

    LaunchedEffect(store.problem) { store.problem?.let { model.toast = it; store.problem = null } }

    Screen("터미널") {
        Page {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardHeader("이 휴대폰의 열쇠")
                    Text(
                        "이 한 줄을 각 맥의 ~/.ssh/authorized_keys 파일에 넣으면 비밀번호 없이 들어가요. 열쇠의 비밀 부분은 이 휴대폰 밖으로 나가지 않아요.",
                        style = AxType.footnote, color = Theme.ax.secondary,
                    )
                    val line = store.publicKeyLine
                    if (line != null) {
                        SelectionContainer {
                            Text(
                                line,
                                style = AxType.caption.copy(fontFamily = FontFamily.Monospace),
                                color = Theme.ax.label, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().background(Theme.ax.page, RoundedCornerShape(10.dp)).padding(10.dp)
                                    .semantics { contentDescription = "공개키" },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("복사", Modifier.weight(1f), kind = ActionKind.Primary, fullWidth = false, icon = Icons.Rounded.ContentCopy) {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("공개키", line))
                                model.toast = "공개키를 복사했어요."
                            }
                            ActionButton("보내기", Modifier.weight(1f), kind = ActionKind.Secondary, fullWidth = false, icon = Icons.Rounded.IosShare) {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line)
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            }
                        }
                        store.fingerprint?.let { fp ->
                            Text(fp, style = AxType.caption2.copy(fontFamily = FontFamily.Monospace), color = Theme.ax.tertiary)
                        }
                    }
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardHeader("맥")
                    if (store.hosts.isEmpty()) {
                        Text("아직 넣은 맥이 없어요. 아래에서 추가해 주세요.", style = AxType.footnote, color = Theme.ax.secondary)
                    }
                    CardList(store.hosts) { host ->
                        HostRow(
                            host,
                            open = { nav.push("terminal-" + host.id) { TerminalScreen(host, store) } },
                            edit = { editing = host },
                            delete = { store.delete(host) },
                        )
                    }
                    ActionButton("맥 추가", Modifier.padding(top = 4.dp), kind = ActionKind.Secondary, icon = Icons.Rounded.Add) { adding = true }
                }
            }
            Text(
                "휴대폰과 맥 모두 Tailscale이 켜져 있어야 해요. 맥은 설정 › 일반 › 공유 › 원격 로그인이 켜져 있어야 해요. 맥을 길게 누르면 고치거나 지울 수 있어요.",
                style = AxType.caption, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "새 열쇠 만들기", style = AxType.footnote, color = SystemColor.red, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { confirmNewKey = true }.padding(vertical = 6.dp),
            )
        }
    }

    if (adding) HostEditSheet(null, onClose = { adding = false }) { store.save(it) }
    editing?.let { host -> HostEditSheet(host, onClose = { editing = null }) { store.save(it) } }
    if (confirmNewKey) {
        MoreActionSheet(
            "새 열쇠를 만들까요?",
            "지금 열쇠는 더 못 써요. 모든 맥의 authorized_keys에 새 공개키를 다시 넣어야 해요.",
            listOf(
                MoreAlertButton("새 열쇠 만들기", MoreAlertButton.Role.Destructive) {
                    if (store.replaceKey()) model.toast = "새 열쇠를 만들었어요. 맥마다 새 공개키를 다시 넣어 주세요."
                },
            ),
            onDismiss = { confirmNewKey = false },
        )
    }
}

/** One Mac: tap opens it, a long press offers 고치기 and 지우기 (iOS context menu). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(host: SSHHost, open: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        CardRow(host.name, host.target, modifier = Modifier.combinedClickable(onClick = open, onLongClick = { menu = true })) { Chevron() }
        DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Theme.ax.card)) {
            DropdownMenuItem(
                text = { Text("고치기", style = AxType.body, color = Theme.ax.label) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Theme.ax.label) },
                onClick = { menu = false; edit() },
            )
            DropdownMenuItem(
                text = { Text("지우기", style = AxType.body, color = SystemColor.red) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = SystemColor.red) },
                onClick = { menu = false; delete() },
            )
        }
    }
}

/** Add or fix one Mac. */
@Composable
private fun HostEditSheet(host: SSHHost?, onClose: () -> Unit, onSave: (SSHHost) -> Unit) {
    var name by remember { mutableStateOf(host?.name ?: "") }
    var user by remember { mutableStateOf(host?.user ?: "") }
    var address by remember { mutableStateOf(host?.address ?: "") }
    var port by remember { mutableStateOf(host?.port?.toString() ?: "22") }
    val built = SSHHost.built(host?.id, name, user, address, port)
    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii)

    AxSheet(onDismiss = onClose) {
        SheetBar(
            if (host == null) "맥 추가" else "맥 고치기",
            leading = { BarButton("취소", onClick = onClose) },
            trailing = { BarButton("저장", bold = true, enabled = built != null) { built?.let { onSave(it); onClose() } } },
        )
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MoreFormSection {
                MoreField(name, { name = it }, "이름 (예: 서버 맥)")
            }
            MoreFormSection(footer = "맥의 사용자 이름은 맥 터미널에서 whoami 로 알 수 있어요.") {
                MoreField(user, { user = it }, "사용자 (맥 로그인 이름)", keyboard = plain)
                Hairline()
                MoreField(address, { address = it }, "주소 (Tailscale 100.x.y.z 또는 이름)", keyboard = plain.copy(keyboardType = KeyboardType.Uri))
                Hairline()
                MoreField(port, { port = it }, "포트", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }
}

/** Ctrl from the keys row: held for the next key, then let go (like SwiftTerm's Ctrl). */
private class RowModifiers : ModifierManager {
    var ctrl by mutableStateOf(false)
    override fun isCtrlActive() = ctrl
    override fun isAltActive() = false
    override fun isShiftActive() = false
    override fun clearTransients() { ctrl = false }
}

private val TerminalTabBarHeight = 76.dp

/** The terminal itself. Leaves when the app goes to the background and reconnects when it comes back. */
@Composable
fun TerminalScreen(host: SSHHost, store: TerminalStore) {
    // Like iOS: the terminal takes the whole height, no floating tab bar.
    val axModel = dev.axkit.app.LocalModel.current
    androidx.compose.runtime.DisposableEffect(Unit) { axModel.hideTabBar = true; onDispose { axModel.hideTabBar = false } }
    val context = LocalContext.current
    val session = remember(host.id) { TerminalSession(host, store) }
    val modifiers = remember { RowModifiers() }
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current

    DisposableEffect(session) {
        session.onCopy = { text -> context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("터미널", text)) }
        onDispose { session.close() }
    }
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (session.isLive) session.disconnect(forBackground = true)
                Lifecycle.Event.ON_START -> if (session.leftForBackground) session.connect()
                else -> {}
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session) {
        withFrameNanos { }   // let the terminal measure itself so the Mac gets the right size
        if (session.state == TerminalSession.State.Idle) session.connect()
        runCatching { focus.requestFocus() }
    }

    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom: Dp = max(ime, navBar + TerminalTabBarHeight)

    CompositionLocalProvider(LocalAx provides DarkAx) {
        Screen(
            host.name,
            trailing = {
                if (session.isLive) BarButton("끊기") { session.disconnect() }
                else BarButton("다시 연결") { session.connect() }
            },
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(bottom = bottom)) {
                Column(Modifier.fillMaxSize()) {
                    Terminal(
                        session.emulator,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        typeface = Typeface.MONOSPACE,
                        initialFontSize = 13.sp,
                        backgroundColor = Color.Black,
                        foregroundColor = Color(0xFFEBEBEB),
                        keyboardEnabled = true,
                        showSoftKeyboard = true,
                        focusRequester = focus,
                        modifierManager = modifiers,
                        onHyperlinkClick = { link ->
                            val uri = runCatching { Uri.parse(link) }.getOrNull()
                            if (uri?.scheme == "https") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                    )
                    KeysRow(session, modifiers, focus)
                }
                Overlay(session, host, Modifier.align(Alignment.Center))
            }
        }
    }

    (session.state as? TerminalSession.State.HostKeyChanged)?.let { changed ->
        MoreAlert(
            "이 맥의 열쇠가 바뀌었어요",
            "${host.name}이(가) 전에 본 것과 다른 열쇠를 보냈어요. 맥을 새로 설치했다면 괜찮지만, 아니라면 누가 중간에 끼어든 것일 수 있어요. 맥에서 ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub 로 확인해 보세요.\n\n새 열쇠: ${changed.newFingerprint}",
            buttons = listOf(
                MoreAlertButton("연결하지 않기", MoreAlertButton.Role.Cancel) { session.disconnect() },
                MoreAlertButton("새 열쇠 믿고 연결", MoreAlertButton.Role.Destructive) { session.trustNewKey(changed.newKey) },
            ),
            onDismiss = {},
        )
    }
}

@Composable
private fun Overlay(session: TerminalSession, host: SSHHost, modifier: Modifier) {
    when (val state = session.state) {
        TerminalSession.State.Idle, TerminalSession.State.Connecting -> Column(
            modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
            Text("${host.name}에 연결하는 중…", style = AxType.subheadline, color = Color.White.copy(alpha = 0.8f))
        }
        is TerminalSession.State.Ended -> Column(
            modifier.padding(24.dp).background(Color(0xE62C2C2E), RoundedCornerShape(Theme.corner)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(state.message, style = AxType.subheadline, color = Color.White, textAlign = TextAlign.Center)
            ActionButton("다시 연결", Modifier.widthIn(max = 220.dp).fillMaxWidth(), kind = ActionKind.Primary, fullWidth = false) { session.connect() }
        }
        TerminalSession.State.Connected, is TerminalSession.State.HostKeyChanged -> {}
    }
}

/** The keys a phone keyboard lacks, over the keyboard: Esc, Ctrl, Tab, ~ | / - and the arrows (SwiftTerm's bar). */
@Composable
private fun KeysRow(session: TerminalSession, modifiers: RowModifiers, focus: FocusRequester) {
    fun mods() = if (modifiers.ctrl) 4 else 0   // libvterm bitmask: 1 Shift, 2 Alt, 4 Ctrl
    fun key(code: Int) {
        if (session.state != TerminalSession.State.Connected) return
        runCatching { session.emulator.dispatchKey(mods(), code) }
        modifiers.ctrl = false
        runCatching { focus.requestFocus() }
    }
    fun char(c: Char) {
        if (session.state != TerminalSession.State.Connected) return
        runCatching { session.emulator.dispatchCharacter(mods(), c.code) }
        modifiers.ctrl = false
        runCatching { focus.requestFocus() }
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF1C1C1E)).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyCap("esc") { key(VTermKey.ESCAPE) }
        KeyCap("ctrl", on = modifiers.ctrl) { modifiers.ctrl = !modifiers.ctrl; runCatching { focus.requestFocus() } }
        KeyCap("tab") { key(VTermKey.TAB) }
        KeyCap("~") { char('~') }
        KeyCap("|") { char('|') }
        KeyCap("/") { char('/') }
        KeyCap("-") { char('-') }
        KeyCap(icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, label = "왼쪽") { key(VTermKey.LEFT) }
        KeyCap(icon = Icons.Rounded.KeyboardArrowUp, label = "위") { key(VTermKey.UP) }
        KeyCap(icon = Icons.Rounded.KeyboardArrowDown, label = "아래") { key(VTermKey.DOWN) }
        KeyCap(icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, label = "오른쪽") { key(VTermKey.RIGHT) }
    }
}

@Composable
private fun KeyCap(label: String, icon: ImageVector? = null, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).widthIn(min = 40.dp)
            .background(if (on) Color.White.copy(alpha = 0.85f) else Color(0xFF3A3A3C), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = Color.White)
        else Text(label, style = AxType.subheadline, color = if (on) Color.Black else Color.White)
    }
}
