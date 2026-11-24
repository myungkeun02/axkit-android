package dev.axkit.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.MarkdownText
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.app.ui.design.loaded
import dev.axkit.core.BlocksView

/** A screen the Mac describes as blocks (ui: "blocks"), so new features need no app update. */
@Composable
fun BlocksScreen(name: String) {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<BlocksView>>(Loadable.Loading) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(BlocksView.serializer(), name) }
    }

    LaunchedEffect(name) { load() }
    Screen(state.loaded?.title ?: "") {
        LoadableView(state, retry = { load() }) { v ->
            MoreRefresh({ load() }) {
                Page {
                    v.blocks.forEach { BlockView(it, name) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockView(b: BlocksView.Block, name: String) {
    val model = LocalModel.current
    when (b.kind) {
        "text" -> MarkdownText(b.text ?: "", style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp))
        "stat" -> Panel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(b.title ?: "", style = AxType.subheadline, color = Theme.ax.secondary)
                Text(b.value ?: "", style = AxType.title, color = Theme.ax.label)
            }
        }
        "list" -> Panel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                b.title?.let { CardHeader(it) }
                // §20: a site the assistant remembers can be forgotten — asked in the chat, where ways.forget_site runs.
                val sites = name == "ways" && (b.title ?: "") in listOf("사이트", "Sites")
                CardList(b.rows ?: emptyList()) { r ->
                    // §34: a row with a message is tapped like an `ask` block; others stay plain.
                    val ask = r.tapMessage
                    val send = { if (ask != null) { model.send(ask, ""); model.toast = "비서에게 부탁했어요" } }
                    val trailing: @Composable RowScope.() -> Unit = {
                        r.trailing?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
                        if (ask != null) Chevron()
                    }
                    if (!sites) {
                        CardRow(r.title, detail = r.detail, modifier = if (ask != null) Modifier.clickable(onClick = send) else Modifier, trailing = trailing)
                    } else Box {
                        var menu by remember { mutableStateOf(false) }
                        CardRow(r.title, detail = r.detail, modifier = Modifier.combinedClickable(onClick = send, onLongClick = { menu = true }), trailing = trailing)
                        DropdownMenu(menu, { menu = false }, containerColor = Theme.ax.card) {
                            DropdownMenuItem(
                                text = { Text("이 사이트 잊기", style = AxType.body, color = SystemColor.red) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = SystemColor.red) },
                                onClick = {
                                    menu = false
                                    val host = r.detail?.split(" · ")?.firstOrNull()?.takeIf { it.contains(".") } ?: r.title
                                    model.send("$host 사이트(${r.title})는 이제 잊어 줘", "")
                                    model.toast = "비서에게 잊어 달라고 했어요"
                                },
                            )
                        }
                    }
                }
            }
        }
        "note" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(SfIcons.of("info.circle"), null, Modifier.size(15.dp), tint = Theme.ax.secondary)
            Text(b.text ?: "", style = AxType.footnote, color = Theme.ax.secondary)
        }
        // Only sends a chat message: anything with effects still goes through the assistant and its cards.
        "ask" -> ActionButton(b.title ?: "비서에게 부탁하기", kind = ActionKind.Primary) {
            model.send(b.message ?: b.title ?: "", "")
            model.toast = "비서에게 부탁했어요"
        }
        else -> {}
    }
}
