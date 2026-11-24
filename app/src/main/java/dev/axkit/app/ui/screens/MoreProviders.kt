package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.ProvidersView
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** AI 연결하기: which AI services the Mac may use; API keys go straight to the Mac's Keychain. */
@Composable
fun ProvidersScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<ProvidersView>>(Loadable.Loading) }
    var connecting by remember { mutableStateOf<ProvidersView.Provider?>(null) }
    var disconnecting by remember { mutableStateOf<ProvidersView.Provider?>(null) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(ProvidersView.serializer(), "providers") }
    }

    LaunchedEffect(Unit) { load() }
    Screen("AI 연결하기", large = true) {
        LoadableView(state, retry = { load() }) { data ->
            MoreRefresh({ load() }) {
                Page {
                    Text(
                        "비서가 쓸 AI를 연결해요. 여러 개를 연결하면 일에 맞게 골라 쓰고, 하나가 막히면 다른 걸 써요.",
                        style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Panel {
                        CardList(data.providers) { p ->
                            val detail = listOfNotNull(p.detail ?: if (p.connected) "연결됨" else "연결 안 됨", if (p.kind == "subscription") "로그인은 맥에서 해요" else null).joinToString(" · ")
                            CardRow(p.label, detail = detail) {
                                if (p.kind == "api_key") {
                                    if (p.connected) ActionButton("끊기", kind = ActionKind.Danger, fullWidth = false, small = true) { disconnecting = p }
                                    else ActionButton("연결", kind = ActionKind.Primary, fullWidth = false, small = true) { connecting = p }
                                } else {
                                    val dot = if (!p.connected) SystemGray3 else if (p.status == "error") SystemColor.red else if (p.status == "warn" || p.status == "degraded") SystemColor.orange else SystemColor.green
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape).background(dot)
                                            .semantics { contentDescription = if (!p.connected) "연결 안 됨" else if (p.status == "error") "문제 있음" else "잘 됨" },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    connecting?.let { p -> ProviderKeySheet(p, onClose = { connecting = null }) { load() } }
    disconnecting?.let { p ->
        MoreActionSheet(
            title = "${p.label} 연결을 끊을까요?",
            message = "맥에 저장된 키가 지워져요. 비서는 다른 AI를 써요.",
            actions = listOf(MoreAlertButton("연결 끊기", MoreAlertButton.Role.Destructive) {
                val client = model.client ?: return@MoreAlertButton
                scope.launch {
                    try { client.disconnectProvider(p.id); model.toast = "${p.label} 연결을 끊었어요" }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { model.toast = e.friendly }
                    load()
                }
            }),
            onDismiss = { disconnecting = null },
        )
    }
}

private val SystemGray3: Color @Composable get() = if (Theme.ax.dark) Color(0xFF48484A) else Color(0xFFC7C7CC)

/** Paste an API key; it is sent once to the Mac and never kept or shown on the phone. */
@Composable
private fun ProviderKeySheet(provider: ProvidersView.Provider, onClose: () -> Unit, done: suspend () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AxSheet(onDismiss = { key = ""; onClose() }) {
        SheetBar(
            "${provider.label} 연결",
            leading = { BarButton("취소") { key = ""; onClose() } },
            trailing = {
                BarButton("맥에 저장", bold = true, enabled = key.trim().length >= 8 && !saving) {
                    saving = true
                    scope.launch {
                        try {
                            model.client?.connectProvider(provider.id, key.trim())
                            key = ""
                            model.toast = "${provider.label}을 연결했어요"
                            done()
                            onClose()
                        } catch (e: CancellationException) { throw e } catch (e: Exception) {
                            model.toast = e.friendly
                        } finally { saving = false }
                    }
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MoreFormSection {
                Text("${provider.label} 사이트에서 만든 API 키를 붙여 넣어 주세요.", style = AxType.body, color = Theme.ax.label, modifier = Modifier.padding(vertical = 12.dp))
                val help = provider.help
                if (help != null) {
                    dev.axkit.app.ui.design.Hairline()
                    Text(help, style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            MoreFormSection(footer = "키는 이 폰에 남지 않고, 맥의 키체인에만 저장돼요. 다시 보여 주지도 않아요.") {
                MoreField(
                    key, { key = it }, "API 키",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    transformation = PasswordVisualTransformation(),
                )
            }
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
