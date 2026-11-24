package dev.axkit.app.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.core.friendly
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Shared building blocks, same look as the iOS app's Theme.swift.

/** The one card used everywhere: white, rounded, on the grey page. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.corner))
            .background(Theme.ax.card)
            .padding(padding),
        content = content,
    )
}

/** A quiet label: small grey text, no fill. */
@Composable
fun Chip(text: String, color: Color? = null) {
    Text(text, style = AxType.caption, color = color ?: Theme.ax.secondary)
}

/** Friendly empty or missing state, always in plain words. */
@Composable
fun EmptyStateView(icon: ImageVector, title: String, message: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(44.dp), tint = Theme.ax.secondary)
        Text(title, style = AxType.headline, color = Theme.ax.label, textAlign = TextAlign.Center)
        if (message != null) Text(message, style = AxType.subheadline, color = Theme.ax.secondary, textAlign = TextAlign.Center)
    }
}

/** Load-once-and-refresh state for a server view. */
sealed interface Loadable<out T> {
    object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

val <T> Loadable<T>.loaded: T? get() = (this as? Loadable.Loaded<T>)?.value

suspend fun <T> loadInto(set: (Loadable<T>) -> Unit, block: suspend () -> T) {
    try { set(Loadable.Loaded(block())) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { set(Loadable.Failed(e.friendly)) }
}

@Composable
fun <T> LoadableView(state: Loadable<T>, retry: suspend () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Loadable.Loading -> Box(Modifier.fillMaxSize().background(Theme.ax.page), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Theme.ax.secondary, strokeWidth = 2.5.dp)
                Text("불러오는 중…", style = AxType.subheadline, color = Theme.ax.secondary)
            }
        }
        is Loadable.Failed -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Column(Modifier.fillMaxSize().background(Theme.ax.page), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                EmptyStateView(Icons.Rounded.WifiOff, "불러오지 못했어요", state.message)
                ActionButton("다시 해 보기", kind = ActionKind.Primary, fullWidth = false) { scope.launch { retry() } }
            }
        }
        is Loadable.Loaded -> content(state.value)
    }
}


/**
 * A tap that only counts when the finger did not travel and not while the bot sheet is being swiped.
 * Used for every control that sends something (iOS SafeTap).
 */
@Composable
fun Modifier.safeTap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val model = LocalModel.current
    return this.clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
        if (!model.isSwiping) onClick()
    }
}

/** Grey page with cards stacked on it. */
@Composable
fun Page(modifier: Modifier = Modifier, top: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().background(Theme.ax.page).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The three button looks of the app: filled (suggested), light (other), light red (stop / no). */
enum class ActionKind { Primary, Secondary, Danger }

@Composable
fun ActionKind.foreground(): Color = when (this) { ActionKind.Primary -> Color.White; ActionKind.Secondary -> Theme.ax.accent; ActionKind.Danger -> SystemColor.red }
@Composable
fun ActionKind.background(): Color = when (this) {
    ActionKind.Primary -> Theme.ax.accent
    ActionKind.Secondary -> Theme.ax.accent.copy(alpha = 0.12f)
    ActionKind.Danger -> SystemColor.red.copy(alpha = 0.1f)
}

@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    kind: ActionKind = ActionKind.Secondary,
    fullWidth: Boolean = true,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    safe: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val model = LocalModel.current
    val shape = RoundedCornerShape(if (small) 10.dp else 12.dp)
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (pressed) 0.7f else if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(kind.background())
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null) {
                if (!(safe && model.isSwiping)) onClick()
            }
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 13.dp),
        horizontalArrangement = if (fullWidth) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(if (small) 16.dp else 18.dp), tint = kind.foreground()); Spacer(Modifier.width(6.dp)) }
        Text(label, style = if (small) AxType.subheadlineBold else AxType.bodyBold, color = kind.foreground())
    }
}

/** Bold card heading with an optional grey line under it. */
@Composable
fun CardHeader(title: String, subtitle: String? = null) {
    Column(Modifier.semantics { heading() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = AxType.headline, color = Theme.ax.label)
        if (subtitle != null) Text(subtitle, style = AxType.subheadline, color = Theme.ax.secondary)
    }
}

/** One line inside a card: title, grey detail under it, something on the right. */
@Composable
fun CardRow(
    title: String,
    detail: String? = null,
    titleColor: Color? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = AxType.body, color = titleColor ?: Theme.ax.label)
            if (!detail.isNullOrEmpty()) Text(detail, style = AxType.footnote, color = Theme.ax.secondary)
        }
        if (trailing != null) trailing()
    }
}

/** Lines inside one card, separated by hairlines. */
@Composable
fun <T> CardList(items: List<T>, row: @Composable (T) -> Unit) {
    Column {
        items.forEachIndexed { index, item ->
            if (index > 0) Hairline()
            row(item)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 0.5.dp, color = Theme.ax.separator)

/** A row that opens another screen: accent icon, title, chevron. */
@Composable
fun MenuRow(icon: ImageVector, title: String, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.width(26.dp).size(22.dp), tint = Theme.ax.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AxType.body, color = Theme.ax.label)
            if (detail != null) Text(detail, style = AxType.footnote, color = Theme.ax.secondary)
        }
        Chevron()
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Theme.ax.tertiary)

/** A label–value line for cards ("기종 · Mac mini"). */
data class KV(val title: String, val value: String, val color: Color? = null)

@Composable
fun KVCard(title: String, items: List<KV>) {
    Panel {
        CardHeader(title)
        Spacer(Modifier.height(4.dp))
        CardList(items) { kv ->
            CardRow(kv.title) { Text(kv.value, style = AxType.body, color = kv.color ?: Theme.ax.secondary, textAlign = TextAlign.End) }
        }
    }
}

/** iOS-style switch (green when on). */
@Composable
fun AxSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = SystemColor.green, checkedThumbColor = Color.White, uncheckedTrackColor = Theme.ax.fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent),
    )
}

/** Grey caption above a group of rows (iOS list section header). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AxType.footnote, color = Theme.ax.secondary, modifier = modifier.padding(start = 16.dp, top = 8.dp))
}

/** A floating rounded "glass" button for the top bar ("오늘", "자동 승인", "찾기"). */
@Composable
fun GlassButton(modifier: Modifier = Modifier, label: String? = null, icon: ImageVector? = null, badge: Boolean = false, onClick: () -> Unit) {
    Box(modifier) {
        Row(
            Modifier
                .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(CircleShape)
                .background(Theme.ax.card.copy(alpha = 0.96f))
                .border(0.5.dp, Theme.ax.separator.copy(alpha = 0.4f), CircleShape)
                .clickable(onClick = onClick)
                .height(44.dp)
                .widthIn(min = 44.dp)
                .padding(horizontal = if (label != null) 16.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) Icon(icon, label, Modifier.size(22.dp), tint = Theme.ax.label)
            if (icon != null && label != null) Spacer(Modifier.width(6.dp))
            if (label != null) Text(label, style = AxType.body, color = Theme.ax.label)
        }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(10.dp).clip(CircleShape).background(SystemColor.red))
    }
}

/** Toast pill at the top; goes away by itself. */
@Composable
fun ToastHost(text: String?, onDone: () -> Unit) {
    LaunchedEffect(text) { if (text != null) { delay(2800); onDone() } }
    AnimatedVisibility(text != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text ?: "", style = AxType.subheadlineBold, color = Theme.ax.label,
                modifier = Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(Theme.ax.card).clickable { onDone() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Single-line text that ends in … */
@Composable
fun Line(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, lines: Int = 1) =
    Text(text, style = style, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = modifier)

/** Content padding helper. */
val PagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
