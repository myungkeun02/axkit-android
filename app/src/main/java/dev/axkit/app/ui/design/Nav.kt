package dev.axkit.app.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A screen pushed on a tab's stack (iOS NavigationStack). The back gesture pops it. */
class Route(val key: String, val content: @Composable () -> Unit)

class NavStack {
    val routes = mutableStateListOf<Route>()
    fun push(key: String, content: @Composable () -> Unit) { routes.add(Route(key + "#" + routes.size, content)) }
    fun pop(): Boolean { if (routes.isEmpty()) return false; routes.removeAt(routes.lastIndex); return true }
    fun popToRoot() { routes.clear() }
}

val LocalNav = staticCompositionLocalOf { NavStack() }

/** Shows the root or the top pushed screen, sliding like iOS; Android back pops. */
@Composable
fun NavHost(nav: NavStack, root: @Composable () -> Unit) {
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = nav.routes.isNotEmpty()) { nav.pop() }
    CompositionLocalProvider(LocalNav provides nav) {
        val top = nav.routes.lastOrNull()
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                val forward = (targetState?.let { nav.routes.indexOf(it) } ?: -1) > (initialState?.let { nav.routes.indexOf(it) } ?: -1)
                if (forward) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                else slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
            },
            label = "nav",
        ) { route ->
            holder.SaveableStateProvider(route?.key ?: "root") {
                if (route == null) root() else route.content()
            }
        }
    }
}

/**
 * iOS navigation bar. `large` = big bold title under the bar (tab roots); otherwise a centred inline title with a back
 * chevron when something is under it on the stack.
 */
@Composable
fun AxTopBar(
    title: String,
    large: Boolean = false,
    showBack: Boolean? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val nav = LocalNav.current
    val back = showBack ?: nav.routes.isNotEmpty()
    Column(Modifier.fillMaxWidth().background(Theme.ax.page).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                if (back) {
                    Row(
                        Modifier.clickable { onBack?.invoke() ?: nav.pop() }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "뒤로", Modifier.size(20.dp), tint = Theme.ax.accent)
                    }
                }
                leading?.invoke(this)
            }
            if (!large) {
                Text(
                    title, style = AxType.headline, color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
                )
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                trailing?.invoke(this)
            }
        }
        if (large) {
            Text(title, style = AxType.largeTitle, color = Theme.ax.label, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        }
    }
}

/** A tab root or pushed screen: top bar + grey page. */
@Composable
fun Screen(
    title: String,
    large: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
        AxTopBar(title, large = large, leading = leading, trailing = trailing)
        dev.axkit.app.ui.screens.RecordingBar()
        banner?.invoke()
        content()
    }
}

/** iOS sheet: a large card from the bottom with its own small bar (title + 닫기/완료). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.ax.page,
        dragHandle = { Box(Modifier.padding(top = 6.dp).size(width = 36.dp, height = 5.dp).background(Theme.ax.tertiary, androidx.compose.foundation.shape.CircleShape)) },
    ) {
        val nav = remember { NavStack() }
        CompositionLocalProvider(LocalNav provides nav) {
            Column(Modifier.fillMaxWidth().fillMaxSize(0.94f), content = content)
        }
    }
}

/** The small bar at the top of a sheet. */
@Composable
fun SheetBar(title: String, leading: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) { leading?.invoke(this) }
        Text(title, style = AxType.headline, color = Theme.ax.label, modifier = Modifier.align(Alignment.Center), maxLines = 1)
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { trailing?.invoke(this) }
    }
}

/** A plain text button in a bar ("닫기", "완료", "추가"). */
@Composable
fun BarButton(label: String, bold: Boolean = false, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    Text(
        label, style = if (bold) AxType.bodyBold else AxType.body,
        color = if (enabled) (color ?: Theme.ax.accent) else Theme.ax.tertiary,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
