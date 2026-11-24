package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import kotlinx.coroutines.launch

// Small pieces shared by the 더보기 screens (iOS ProgressView, segmented Picker, Form sections, alerts).

/** SF Symbols the 더보기 menu uses that the shared map doesn't cover yet. */
fun moreIcon(name: String?): ImageVector = when (name) {
    "repeat" -> Icons.Rounded.Repeat
    "waveform" -> Icons.Rounded.GraphicEq
    "cpu" -> Icons.Rounded.Memory
    "sparkle" -> Icons.Rounded.AutoAwesome
    "hourglass" -> Icons.Rounded.HourglassTop
    "stethoscope" -> Icons.Rounded.MonitorHeart
    "safari" -> Icons.Rounded.Explore
    "folder", "folder.badge.gearshape" -> Icons.Rounded.Folder
    else -> SfIcons.of(name)
}

/** iOS `.refreshable`: pull down to reload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreRefresh(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; try { onRefresh() } finally { refreshing = false } } },
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/** iOS linear ProgressView: thin rounded bar on a light grey track. */
@Composable
fun MoreProgressBar(value: Double, color: Color = Theme.ax.accent, modifier: Modifier = Modifier) {
    val v = value.coerceIn(0.0, 1.0).toFloat()
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MoreTrack)) {
        if (v > 0f) Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(CircleShape).background(color))
    }
}

val MoreTrack: Color @Composable get() = if (Theme.ax.dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

/** iOS switch tinted with the accent colour (`.tint(.accentColor)`). */
@Composable
fun MoreAccentSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Theme.ax.accent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Theme.ax.fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** iOS segmented Picker. */
@Composable
fun <T> MoreSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(Theme.ax.fill).padding(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(if (Theme.ax.dark) Color(0xFF636366) else Color.White) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) AxType.footnoteBold else AxType.footnote, color = Theme.ax.label)
            }
        }
    }
}

/** An iOS Form section: optional grey header, white rounded group, optional grey footer. */
@Composable
fun MoreFormSection(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) Text(header, style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Theme.ax.card).padding(horizontal = 16.dp), content = content)
        if (footer != null) Text(footer, style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    }
}

/** A plain text field with a grey placeholder (iOS TextField / SecureField inside a Form). */
@Composable
fun MoreField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    transformation: VisualTransformation = VisualTransformation.None,
    style: TextStyle = AxType.body,
) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = style.copy(color = Theme.ax.label),
        cursorBrush = SolidColor(Theme.ax.accent),
        keyboardOptions = keyboard, visualTransformation = transformation,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = style, color = Theme.ax.tertiary)
                inner()
            }
        },
    )
}

/** An iOS alert button. */
data class MoreAlertButton(val label: String, val role: Role = Role.Default, val action: () -> Unit) {
    enum class Role { Default, Cancel, Destructive }
}

/** iOS `.alert`: centred card, title, optional message / extra content, buttons under hairlines. */
@Composable
fun MoreAlert(
    title: String,
    message: String? = null,
    buttons: List<MoreAlertButton>,
    onDismiss: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(270.dp).clip(RoundedCornerShape(14.dp)).background(Theme.ax.card)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = AxType.headline, color = Theme.ax.label, textAlign = TextAlign.Center)
                if (message != null) Text(message, style = AxType.footnote, color = Theme.ax.label, textAlign = TextAlign.Center)
                content?.invoke(this)
            }
            val sideBySide = buttons.size == 2
            if (sideBySide) {
                Hairline()
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    buttons.forEachIndexed { i, b ->
                        if (i > 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(Theme.ax.separator))
                        AlertButtonText(b, Modifier.weight(1f).fillMaxHeight(), onDismiss)
                    }
                }
            } else {
                buttons.forEach { b ->
                    Hairline()
                    AlertButtonText(b, Modifier.fillMaxWidth().height(44.dp), onDismiss)
                }
            }
        }
    }
}

@Composable
private fun AlertButtonText(b: MoreAlertButton, modifier: Modifier, onDismiss: () -> Unit) {
    Box(modifier.clickable { onDismiss(); b.action() }, contentAlignment = Alignment.Center) {
        Text(
            b.label,
            style = if (b.role == MoreAlertButton.Role.Cancel) AxType.bodyBold else AxType.body,
            color = if (b.role == MoreAlertButton.Role.Destructive) SystemColor.red else Theme.ax.accent,
        )
    }
}

/** iOS `.confirmationDialog`: a sheet of actions from the bottom with a separate 취소. */
@Composable
fun MoreActionSheet(title: String, message: String?, actions: List<MoreAlertButton>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.widthIn(max = 500.dp).fillMaxWidth().navigationBarsPadding().padding(8.dp).clickable(interactionSource = null, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Theme.ax.card)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = AxType.footnoteBold, color = Theme.ax.secondary, textAlign = TextAlign.Center)
                        if (message != null) Text(message, style = AxType.footnote, color = Theme.ax.secondary, textAlign = TextAlign.Center)
                    }
                    actions.forEach { a ->
                        Hairline()
                        AlertButtonText(a, Modifier.fillMaxWidth().height(56.dp), onDismiss)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Theme.ax.card).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("취소", style = AxType.bodyBold, color = Theme.ax.accent) }
            }
        }
    }
}

/** Several icon buttons in one floating glass capsule (iOS ToolbarItemGroup). */
@Composable
fun MoreGlassGroup(items: List<Triple<ImageVector, String, () -> Unit>>) {
    Row(
        Modifier
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(CircleShape)
            .background(Theme.ax.card.copy(alpha = 0.96f))
            .border(0.5.dp, Theme.ax.separator.copy(alpha = 0.4f), CircleShape)
            .height(44.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (icon, label, action) ->
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) {
                Icon(icon, label, Modifier.size(24.dp), tint = Theme.ax.label)
            }
        }
    }
}

internal val MoreChevronLeft = Icons.AutoMirrored.Rounded.KeyboardArrowLeft
internal val MoreChevronRight = Icons.AutoMirrored.Rounded.KeyboardArrowRight
internal val TabularNums = TextStyle(fontFeatureSettings = "tnum")
