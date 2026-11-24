package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.GlassButton
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.background
import dev.axkit.app.ui.design.foreground
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.AutonomyView
import dev.axkit.core.Format
import dev.axkit.core.ModelTiersView
import dev.axkit.core.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Grey page with pull-to-refresh, used by both screens here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutonomyRefreshPage(reload: suspend () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) { Page(content = content) }
}

/**
 * 알아서 하는 일: rules the owner granted from a card ("앞으로는 알아서"). Any of them can be taken back here.
 * With [onClose] it is the 결재 tab's 자동 승인 sheet: inline title and 닫기, no back chevron.
 */
@Composable
fun AutonomyScreen(onClose: (() -> Unit)? = null) {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<AutonomyView>>(Loadable.Loading) }
    var stopping by remember { mutableStateOf<AutonomyView.Rule?>(null) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(AutonomyView.serializer(), "autonomy") }
    }
    LaunchedEffect(Unit) { load() }

    val body: @Composable ColumnScope.() -> Unit = {
        LoadableView(state, retry = { load() }) { data ->
            AutonomyRefreshPage({ load() }) {
                Text(
                    "결재에서 '앞으로는 알아서'를 고르면 여기에 생겨요. 한 일은 캘린더의 '한 일'에 남아요.",
                    style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardHeader("묻지 않고 하는 일")
                        if (data.rules.isEmpty()) {
                            Text("아직 없어요", style = AxType.body, color = Theme.ax.secondary, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        CardList(data.rules) { rule ->
                            CardRow(
                                rule.label,
                                listOfNotNull(rule.since?.let { "${Format.relative(it)}부터" }, rule.uses?.let { "${it}번 했어요" }).joinToString(" · "),
                            ) {
                                ActionButton("그만", kind = ActionKind.Danger, fullWidth = false, small = true) { stopping = rule }
                            }
                        }
                    }
                }
                val always = data.alwaysAsk
                if (!always.isNullOrEmpty()) {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("이건 언제나 물어봐요")
                            CardList(always) { CardRow(it) }
                        }
                    }
                }
            }
        }
    }

    if (onClose != null) {
        Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
            SheetBar("알아서 하는 일", trailing = { BarButton("닫기") { onClose() } })
            body()
        }
    } else {
        Screen("알아서 하는 일", large = true, content = body)
    }

    stopping?.let { rule ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            containerColor = Theme.ax.card,
            title = { Text("'${rule.label}'를 그만할까요?", style = AxType.headline, color = Theme.ax.label) },
            confirmButton = {
                TextButton(onClick = {
                    stopping = null
                    model.scope.launch {
                        try {
                            model.client?.viewAction("autonomy", "revoke", buildJsonObject { put("id", JsonPrimitive(rule.id)) })
                            model.toast = "다음부터 다시 물어볼게요"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            model.toast = e.friendly
                        }
                        load()
                    }
                }) { Text("그만하고 다시 물어보기", style = AxType.bodyBold, color = SystemColor.red) }
            },
            dismissButton = {
                TextButton(onClick = { stopping = null }) { Text("취소", style = AxType.body, color = Theme.ax.accent) }
            },
        )
    }
}

/** AI 등급과 용도: which models count as S/A/B/C (best first inside each), and which grade each job uses. */
@Composable
fun ModelTiersScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<ModelTiersView>>(Loadable.Loading) }
    var reordering by remember { mutableStateOf(false) }

    suspend fun load() {
        val client = model.client ?: return
        loadInto({ state = it }) { client.view(ModelTiersView.serializer(), "modeltiers") }
    }
    LaunchedEffect(Unit) { load() }

    fun act(action: String, body: kotlinx.serialization.json.JsonObject) {
        model.scope.launch {
            try { model.client?.viewAction("modeltiers", action, body) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { model.toast = e.friendly }
            load()
        }
    }

    fun setPurpose(purpose: ModelTiersView.Purpose, tier: String) =
        act("purpose", buildJsonObject { put("id", JsonPrimitive(purpose.id)); put("tier", JsonPrimitive(tier)) })

    fun move(tier: ModelTiersView.Tier, from: Int, to: Int) {
        val ids = tier.models.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        val id = ids.removeAt(from)
        ids.add(to, id)
        act("rank", buildJsonObject { put("tier", JsonPrimitive(tier.id)); put("order", JsonArray(ids.map { JsonPrimitive(it) })) })
    }

    Screen(
        "AI 등급과 용도", large = true,
        trailing = { GlassButton(label = if (reordering) "완료" else "순서 바꾸기") { reordering = !reordering } },
    ) {
        LoadableView(state, retry = { load() }) { data ->
            AutonomyRefreshPage({ load() }) {
                Text(
                    "일마다 어느 등급의 AI를 쓸지 정해요. 같은 등급 안에서는 위에 있는 모델부터 쓰고, 막히면 다음 모델로 넘어가요.",
                    style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
                val changes = data.changes
                if (!changes.isNullOrEmpty()) {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader("AI가 최근 바꾼 것", data.changedAt?.let { "${Format.relative(it)} · 새 모델과 사용량을 보고 정했어요" })
                            CardList(changes) { CardRow(it.text, it.at?.let { at -> Format.relative(at) }) }
                        }
                    }
                }
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardHeader("용도별 등급")
                        CardList(data.purposes) { purpose ->
                            CardRow(purpose.label, purpose.calls7d?.let { "최근 7일 ${it}번" }) {
                                TierMenu(data.tiers, purpose) { tier -> setPurpose(purpose, tier) }
                            }
                        }
                    }
                }
                data.tiers.forEach { tier ->
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardHeader(tier.label, tier.summary)
                            CardList(tier.models.withIndex().toList()) { (index, m) ->
                                val off = m.available == false
                                CardRow(
                                    "${index + 1}. ${m.label}",
                                    listOfNotNull(m.provider.ifEmpty { null }, if (off) "연결 안 됨" else null, m.note).joinToString(" · "),
                                    titleColor = if (off) Theme.ax.secondary else Theme.ax.label,
                                ) {
                                    if (reordering) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TierArrowButton(SfIcons.of("chevron.up"), "${m.label} 위로", enabled = index > 0) { move(tier, index, index - 1) }
                                            TierArrowButton(SfIcons.of("chevron.down"), "${m.label} 아래로", enabled = index < tier.models.size - 1) { move(tier, index, index + 1) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The grade pill on a 용도 row; tapping opens the list of grades with a check on the current one. */
@Composable
private fun TierMenu(tiers: List<ModelTiersView.Tier>, purpose: ModelTiersView.Purpose, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val kind = ActionKind.Secondary
    Box {
        Text(
            tiers.firstOrNull { it.id == purpose.tier }?.label ?: purpose.tier,
            style = AxType.subheadlineBold, color = kind.foreground(),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(kind.background())
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "${purpose.label} 등급 바꾸기" }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Theme.ax.card) {
            tiers.forEach { tier ->
                DropdownMenuItem(
                    text = { Text(tier.label, style = AxType.body, color = Theme.ax.label) },
                    leadingIcon = {
                        if (tier.id == purpose.tier) Icon(SfIcons.of("checkmark"), null, Modifier.size(18.dp), tint = Theme.ax.label)
                        else Spacer(Modifier.width(18.dp))
                    },
                    onClick = { open = false; onPick(tier.id) },
                )
            }
        }
    }
}

/** Small light-blue arrow button (iOS `.action(.secondary, small)` with a chevron). */
@Composable
private fun TierArrowButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val kind = ActionKind.Secondary
    Box(
        Modifier.alpha(if (pressed) 0.7f else if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(10.dp)).background(kind.background())
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = kind.foreground()) }
}
