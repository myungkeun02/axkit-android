package dev.axkit.app.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.axkit.app.AppModel
import dev.axkit.app.BuildConfig
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.PairingLink
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Plain words for a failed pairing (same as iOS PairView.message). */
internal fun pairErrorMessage(error: Throwable): String {
    if (error is PairingLink.ParseException) {
        return when (error.reason) {
            PairingLink.ParseError.InsecureAddress -> "안전하지 않은 주소예요. https 주소나 집·Tailscale 주소만 연결할 수 있어요."
            else -> "ax-kit 연결 QR이 아니에요. 맥에 뜬 QR을 다시 찍어 주세요."
        }
    }
    return error.friendly
}

/** First screen and "다른 맥에 연결": scan the Mac's QR, or paste its link. */
@Composable
fun PairView(isOnboarding: Boolean = false) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val inSheet = LocalSettingsInSheet.current
    var scanning by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect(link: String) {
        working = true
        error = null
        // The model's scope: pairing must finish even if this screen goes away (it does, once paired).
        model.scope.launch {
            try {
                model.pair(link)
                model.toast = "맥과 연결됐어요"
                if (!isOnboarding) nav.pop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = pairErrorMessage(e)
            } finally {
                working = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
        if (!isOnboarding) PairBackBar(inSheet) { nav.pop() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .then(if (isOnboarding) Modifier.statusBarsPadding() else Modifier)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(SfIcons.of("macmini.fill"), null, Modifier.size(52.dp), tint = Theme.ax.accent)
                Text(if (isOnboarding) "내 맥과 연결해요" else "다른 맥에 연결", style = AxType.largeTitle, color = Theme.ax.label)
                Text("비서는 집에 있는 맥에서 일해요. 휴대폰은 그 맥과 이야기하는 창이에요.", style = AxType.body, color = Theme.ax.secondary)
            }

            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PairStep(1, "맥의 터미널에서 이렇게 입력해요", "ax-kit phone pair --app --platform android")
                    PairStep(2, "맥 화면에 뜬 QR을 아래 버튼으로 찍어요")
                    PairStep(3, "끝! 이제 어디서든 비서와 이야기할 수 있어요")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PairButton("QR 찍기", SfIcons.of("qrcode.viewfinder"), PairButtonStyle.Prominent, enabled = !working) { scanning = true }
                PairButton("연결 링크 붙여넣기", SfIcons.of("link"), PairButtonStyle.Bordered, enabled = !working) { pasting = true }
                if (BuildConfig.SHOW_MOCK) {
                    PairButton("개발용 가짜 맥에 연결", SfIcons.of("hammer"), PairButtonStyle.Borderless, enabled = !working) {
                        connect(AppModel.MOCK_LINK)
                    }
                }
            }

            if (working) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Theme.ax.secondary, strokeWidth = 2.5.dp)
                    Text("맥과 연결하는 중…", style = AxType.subheadline, color = Theme.ax.secondary)
                }
            }
            error?.let { message ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(SfIcons.of("exclamationmark.triangle.fill"), null, Modifier.size(18.dp), tint = SystemColor.red)
                    Text(message, style = AxType.subheadline, color = SystemColor.red)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(SfIcons.of("lock.shield"), null, Modifier.size(16.dp), tint = Theme.ax.secondary)
                Text(
                    "집 밖에서도 쓰려면 휴대폰에 Tailscale 앱을 켜 두세요. 연결 정보는 이 휴대폰 안에만 잠가서 저장돼요.",
                    style = AxType.footnote, color = Theme.ax.secondary,
                )
            }
        }
    }

    if (scanning) {
        QRScannerSheet(onDismiss = { scanning = false }) { code ->
            scanning = false
            connect(code)
        }
    }

    if (pasting) {
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = Theme.ax.card,
            title = { Text("연결 링크 붙여넣기", style = AxType.headline, color = Theme.ax.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("맥에 나온 'axkit://pair' 로 시작하는 링크를 넣어 주세요.", style = AxType.subheadline, color = Theme.ax.secondary)
                    OutlinedTextField(
                        value = linkText, onValueChange = { linkText = it }, singleLine = true,
                        placeholder = { Text("axkit://pair?…", style = AxType.body, color = Theme.ax.tertiary) },
                        textStyle = AxType.body.copy(color = Theme.ax.label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pasting = false; connect(linkText) }) { Text("연결", style = AxType.bodyBold, color = Theme.ax.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pasting = false }) { Text("취소", style = AxType.body, color = Theme.ax.accent) }
            },
        )
    }
}

/** Back chevron only (the big title is in the page), like iOS when pushed from 설정. */
@Composable
private fun PairBackBar(inSheet: Boolean, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Theme.ax.page)
            .then(if (inSheet) Modifier else Modifier.statusBarsPadding())
            .height(56.dp).padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "뒤로", Modifier.size(20.dp), tint = Theme.ax.accent)
        }
    }
}

@Composable
private fun PairStep(number: Int, text: String, code: String? = null) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Theme.ax.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text("$number", style = AxType.subheadlineBold, color = Theme.ax.accent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = AxType.body, color = Theme.ax.label)
            if (code != null) {
                SelectionContainer {
                    Text(
                        code, style = AxType.footnote.copy(fontFamily = FontFamily.Monospace), color = Theme.ax.label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Theme.ax.fill).padding(8.dp),
                    )
                }
            }
        }
    }
}

private enum class PairButtonStyle { Prominent, Bordered, Borderless }

/** iOS large capsule buttons: filled blue, grey with blue text, and plain blue text. */
@Composable
private fun PairButton(label: String, icon: ImageVector, style: PairButtonStyle, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when (style) {
        PairButtonStyle.Prominent -> Theme.ax.accent
        PairButtonStyle.Bordered -> Theme.ax.fill
        PairButtonStyle.Borderless -> Color.Transparent
    }
    val fg = if (style == PairButtonStyle.Prominent) Color.White else Theme.ax.accent
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (pressed) 0.7f else if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = if (style == PairButtonStyle.Borderless) 8.dp else 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(label, style = AxType.title3.copy(fontWeight = AxType.body.fontWeight), color = fg)
    }
}
