package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxSwitch
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.BotAvatar
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.background
import dev.axkit.app.ui.design.foreground
import dev.axkit.core.BotsView
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

val tierChoices = listOf("S", "A", "B", "C")

fun tierWords(tier: String): String = when (tier) {
    "S" -> "S · 가장 똑똑함"
    "A" -> "A · 똑똑하고 빠름"
    "B" -> "B · 싸고 빠름"
    else -> "C · 아주 쌈"
}

/** iOS segmented picker: grey track, the chosen segment on a white pill. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(Theme.ax.fill).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxWidth().height(28.dp)
                    .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (Theme.ax.dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = if (on) AxType.footnoteBold else AxType.footnote, color = Theme.ax.label, maxLines = 1) }
        }
    }
}

/** Grade picker in the app's button style: four small segments. */
@Composable
fun TierPicker(title: String, tier: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
        Segmented(tierChoices, tierChoices.indexOf(tier), Modifier.width(180.dp)) { onChange(tierChoices[it]) }
    }
}

/** iOS Stepper: label on the left, − | + on the right. */
@Composable
internal fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
        Row(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(Theme.ax.fill), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(47.dp).height(32.dp).clickable(enabled = value > range.first) { onChange(value - 1) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, "빼기", Modifier.size(18.dp), tint = if (value > range.first) Theme.ax.label else Theme.ax.tertiary) }
            Box(Modifier.width(0.5.dp).height(18.dp).background(Theme.ax.separator))
            Box(
                Modifier.width(47.dp).height(32.dp).clickable(enabled = value < range.last) { onChange(value + 1) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "더하기", Modifier.size(18.dp), tint = if (value < range.last) Theme.ax.label else Theme.ax.tertiary) }
        }
    }
}

/** A plain iOS text field inside a card: no border, grey placeholder. */
@Composable
fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle = AxType.body, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = style.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Theme.ax.tertiary, maxLines = 1)
                inner()
            }
        },
    )
}

@Serializable private data class Made(val teamId: String = "", val channel: String = "")

/**
 * 팀 추가: every team is a lead + staff (+ reviewers). The owner sets the size and each role's AI grade,
 * or lets the assistant decide.
 */
@Composable
fun AddTeamSheet(onDismiss: () -> Unit, opened: (String) -> Unit) {
    val model = LocalModel.current
    val groups = model.bots?.groups.orEmpty()
    var groupId by remember { mutableStateOf(groups.firstOrNull()?.id ?: "") }
    var newGroup by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var isDev by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    var staff by remember { mutableStateOf(3) }
    var reviewers by remember { mutableStateOf(0) }
    var leadTier by remember { mutableStateOf("A") }
    var staffTier by remember { mutableStateOf("B") }
    var reviewerTier by remember { mutableStateOf("S") }
    var saving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val ready = name.isNotBlank() && (groupId != "" || newGroup.isNotBlank())

    fun save() {
        val client = model.client ?: return
        saving = true
        model.scope.launch {
            try {
                val body = buildJsonObject {
                    put("name", name.trim())
                    put("kind", if (isDev) "dev" else "general")
                    if (purpose.isNotEmpty()) put("purpose", purpose)
                    if (groupId.isEmpty()) put("newGroupLabel", newGroup.trim()) else put("group", groupId)
                    if (auto) put("auto", JsonPrimitive(true))
                    else {
                        put("staffCount", staff)
                        put("reviewerCount", reviewers)
                        putJsonObject("tiers") {
                            put("lead", leadTier)
                            put("staff", staffTier)
                            if (reviewers > 0) put("reviewer", reviewerTier)
                        }
                    }
                }
                val made = client.viewAction(Made.serializer(), "bots", "createTeam", body)
                model.refreshBasics()
                model.toast = "팀을 만들었어요"
                onDismiss()
                opened(made.channel)
            } catch (e: Exception) {
                model.toast = e.friendly
            } finally {
                saving = false
            }
        }
    }

    AxSheet(onDismiss = onDismiss) {
        SheetBar("팀 추가", leading = { BarButton("취소") { onDismiss() } })
        Page(top = 8.dp) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        Row(Modifier.fillMaxWidth().clickable { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text("어느 회사", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                            Text(groups.firstOrNull { it.id == groupId }?.label ?: "새 회사…", style = AxType.body, color = Theme.ax.secondary)
                            Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(16.dp), tint = Theme.ax.secondary)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            groups.forEach { g -> DropdownMenuItem(text = { Text(g.label) }, onClick = { groupId = g.id; menu = false }) }
                            DropdownMenuItem(text = { Text("새 회사…") }, onClick = { groupId = ""; menu = false })
                        }
                    }
                    if (groupId == "") PlainField(newGroup, { newGroup = it }, "새 회사 이름")
                    Hairline()
                    PlainField(name, { name = it }, "팀 이름 (예: 디자인)", style = AxType.headline)
                    PlainField(purpose, { purpose = it }, "하는 일 (예: 화면 시안을 만들어요)")
                    Hairline()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("개발팀 (끝난 일을 검수해요)", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                        AxSwitch(isDev) { dev ->
                            isDev = dev
                            reviewers = if (dev) maxOf(reviewers, 1) else 0
                        }
                    }
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segmented(listOf("직접 정하기", "AI에게 맡기기"), if (auto) 1 else 0, Modifier.fillMaxWidth()) { auto = it == 1 }
                    if (auto) {
                        Text(
                            "비서가 팀이 할 일을 보고 직원 수, 검수 수, AI 등급을 정해요. 나중에 팀 설정에서 바꿀 수 있어요.",
                            style = AxType.subheadline, color = Theme.ax.secondary,
                        )
                    } else {
                        Text("팀장 1명", style = AxType.subheadlineBold, color = Theme.ax.label)
                        Stepper("직원 ${staff}명", staff, 1..6) { staff = it }
                        Stepper("검수 ${reviewers}명", reviewers, 0..3) { reviewers = it }
                        Hairline()
                        Text("AI 등급", style = AxType.subheadlineBold, color = Theme.ax.label)
                        TierPicker("팀장", leadTier) { leadTier = it }
                        TierPicker("직원", staffTier) { staffTier = it }
                        if (reviewers > 0) TierPicker("검수", reviewerTier) { reviewerTier = it }
                        Text("S가 가장 똑똑하고 비싸요. 보통 팀장 A, 직원 B, 검수 S로 시작해요.", style = AxType.caption, color = Theme.ax.secondary)
                    }
                }
            }
            BusyButton("팀 만들기", busy = saving, enabled = ready && !saving) { save() }
        }
    }
}

/** Primary action button with a spinner on the right while working ("팀 만들기"). */
@Composable
private fun BusyButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(12.dp))
            .background(ActionKind.Primary.background()).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AxType.bodyBold, color = ActionKind.Primary.foreground())
        if (busy) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

private fun rankWords(rank: String?): String = when (rank) { "lead" -> "팀장"; "reviewer" -> "검수"; else -> "직원" }

/** 팀 설정: who is in the team and each role's AI grade (changeable any time). */
@Composable
fun TeamSheet(team: BotsView.Team, onDismiss: () -> Unit) {
    val model = LocalModel.current
    val tiers = remember { mutableStateMapOf<String, String>() }
    val members = model.bots?.members(team).orEmpty()
    val order = listOf("lead", "staff", "reviewer")

    fun save(rank: String, tier: String) {
        val client = model.client ?: return
        model.scope.launch {
            try {
                client.viewAction("bots", "setTiers", buildJsonObject {
                    put("teamId", team.id)
                    putJsonObject("tiers") { put(rank, tier) }
                })
                model.refreshBasics()
            } catch (e: Exception) { model.toast = e.friendly }
        }
    }

    AxSheet(onDismiss = onDismiss) {
      val nav = remember { dev.axkit.app.ui.design.NavStack() }
      dev.axkit.app.ui.design.NavHost(nav) {
       Column {
        SheetBar(team.name, trailing = { BarButton("닫기", bold = true) { onDismiss() } })
        Page(top = 8.dp) {
            if (team.decidedBy == "ai") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardHeader("AI가 정한 구성이에요", team.reason)
                        Text("아래에서 바꾸면 그다음부터는 정한 대로 해요.", style = AxType.caption, color = Theme.ax.secondary)
                    }
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardHeader("팀원 ${members.size}명", team.purpose)
                    CardList(members) { bot ->
                        Row(
                            Modifier.clickable { nav.push("bot-${bot.id}") { BotInstructionsScreen(bot.id) } },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BotAvatar(bot.name, size = 32.dp, avatar = bot.avatar, status = bot.status)
                            CardRow(bot.name, rankWords(bot.rank) + " · 일하는 방식 보기") {
                                Text(bot.tier?.let { "$it 등급" } ?: "", style = AxType.body, color = Theme.ax.secondary)
                                dev.axkit.app.ui.design.Chevron()
                            }
                        }
                    }
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardHeader("역할별 AI 등급")
                    order.filter { rank -> members.any { it.rank == rank } }.forEach { rank ->
                        TierPicker(rankWords(rank), tiers[rank] ?: members.firstOrNull { it.rank == rank }?.tier ?: "B") { value ->
                            tiers[rank] = value
                            save(rank, value)
                        }
                    }
                }
            }
        }
       }
      }
    }
}

/** Title bar of a team or meeting room: faces + name; tap for team settings, or open a meeting. */
@Composable
fun RoomHeader(room: String) {
    val model = LocalModel.current
    val bots = model.bots
    val team = bots?.teamForChannel(room)
    val group = if (team == null) bots?.groupForMeeting(room) else null
    when {
        team != null -> {
            var showTeam by remember { mutableStateOf(false) }
            val members = bots.members(team)
            Row(
                Modifier.clickable { showTeam = true }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarStack(members.map { it.name }, size = 24.dp, working = members.firstOrNull()?.status == "working")
                Column {
                    Text(team.name, style = AxType.headline, color = Theme.ax.label, maxLines = 1)
                    Text("팀원 ${members.size}명 · 설정", style = AxType.caption2, color = Theme.ax.secondary)
                }
            }
            if (showTeam) TeamSheet(team) { showTeam = false }
        }
        group != null -> {
            var asking by remember { mutableStateOf(false) }
            var topic by remember { mutableStateOf("") }
            val canStart = model.capabilities.hasAction("bots", "meeting")
            val leads = group.bots.filter { it.rank == "lead" }
            Row(
                Modifier.clickable(enabled = canStart) { asking = true }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarStack(leads.map { it.name }, size = 22.dp)
                Column {
                    Text("팀장회의", style = AxType.headline, color = Theme.ax.label, maxLines = 1)
                    Text(
                        if (canStart) "눌러서 회의 열기" else "팀장 ${leads.size}명",
                        style = AxType.caption2, color = if (canStart) Theme.ax.accent else Theme.ax.secondary,
                    )
                }
            }
            if (asking) {
                AlertDialog(
                    onDismissRequest = { asking = false },
                    containerColor = Theme.ax.card,
                    title = { Text("무엇에 대해 회의할까요?", style = AxType.headline, color = Theme.ax.label) },
                    text = {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Theme.ax.page)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) { PlainField(topic, { topic = it }, "예: 다음 달 신메뉴") }
                    },
                    confirmButton = {
                        BarButton("회의 열기", bold = true) {
                            asking = false
                            val t = topic; topic = ""
                            val client = model.client
                            model.scope.launch {
                                try {
                                    client?.viewAction("bots", "meeting", buildJsonObject { put("group", group.id); put("topic", t) })
                                } catch (e: Exception) { model.toast = e.friendly }
                            }
                        }
                    },
                    dismissButton = { BarButton("취소") { asking = false } },
                )
            }
        }
        else -> {
            val bot = bots?.botForChannel(room)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotAvatar(bot?.name ?: model.roomTitle(room), size = 28.dp, avatar = bot?.avatar, status = bot?.status)
                Column {
                    Text(model.roomTitle(room), style = AxType.headline, color = Theme.ax.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (bot != null) Text(
                        if (bot.status == "working") "일하는 중" else dev.axkit.core.ChatText.preview(bot.role ?: "", 30),
                        style = AxType.caption2, color = Theme.ax.secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Overlapping faces (up to three), each on a thin ring of the page colour. */
@Composable
fun AvatarStack(names: List<String>, size: Dp, working: Boolean = false) {
    val shown = names.take(3)
    val ring = if (Theme.ax.dark) Color.Black else Color.White
    val width = size * (1f + 0.65f * (maxOf(minOf(names.size, 3), 1) - 1))
    Box(Modifier.width(width).height(size)) {
        shown.forEachIndexed { i, name ->
            Box(
                Modifier.offset(x = size * 0.65f * i).size(size)
                    .drawBehind { drawCircle(ring, radius = this.size.minDimension / 2 + 1.5.dp.toPx()) },
                contentAlignment = Alignment.Center,
            ) { BotAvatar(name, size = size, status = if (i == 0 && working) "working" else null) }
        }
    }
}

/** Red count capsule ("새 글 3개"). */
@Composable
fun UnreadBadge(count: Int) {
    if (count <= 0) return
    Text(
        "$count", style = AxType.captionBold, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(dev.axkit.app.ui.design.SystemColor.red)
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = "새 글 ${count}개" },
    )
}
