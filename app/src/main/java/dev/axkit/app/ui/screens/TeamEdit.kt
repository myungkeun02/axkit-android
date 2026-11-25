package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.BotEdit
import dev.axkit.core.BotsView
import dev.axkit.core.TeamFromText
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Sends one §37 edit and reloads the bots; returns null when it worked, else what went wrong in plain words. */
private suspend fun sendEdit(model: dev.axkit.app.AppModel, action: String, fields: Map<String, String>): String? {
    val client = model.client ?: return "맥과 연결되어 있지 않아요."
    return try {
        client.viewAction("bots", action, JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
        model.refreshBasics()
        null
    } catch (e: Exception) { e.friendly }
}

/** 팀 설정 top: the team's name and what it does, changeable in place (§37 renameTeam). */
@Composable
fun TeamNamePanel(team: BotsView.Team) {
    val model = LocalModel.current
    val canRename = model.capabilities.hasAction("bots", "renameTeam")
    var editing by remember(team.id) { mutableStateOf(false) }
    var name by remember(team.id, team.name) { mutableStateOf(team.name) }
    var purpose by remember(team.id, team.purpose) { mutableStateOf(team.purpose ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!editing) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { CardHeader(team.name, team.purpose) }
                    if (canRename) Text("고치기", style = AxType.subheadlineBold, color = Theme.ax.accent, modifier = Modifier.clickable { editing = true }.padding(4.dp))
                }
            } else {
                Text("팀 이름", style = AxType.footnote, color = Theme.ax.secondary)
                BoxField(name, { name = it }, "팀 이름")
                Text("하는 일", style = AxType.footnote, color = Theme.ax.secondary)
                BoxField(purpose, { purpose = it }, "이 팀이 하는 일", lines = 3)
                problem?.let { Text(it, style = AxType.footnote, color = SystemColor.red) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(if (saving) "저장하는 중…" else "저장", kind = ActionKind.Primary, fullWidth = false, small = true, enabled = !saving) {
                        when (val r = BotEdit.renameTeam(team, name, purpose)) {
                            is BotEdit.Result.Problem -> problem = r.text
                            is BotEdit.Result.Body -> {
                                saving = true; problem = null
                                model.scope.launch {
                                    problem = sendEdit(model, "renameTeam", r.fields)
                                    saving = false
                                    if (problem == null) { editing = false; model.toast = "팀 이름을 바꿨어요" }
                                }
                            }
                        }
                    }
                    ActionButton("취소", fullWidth = false, small = true) { editing = false; name = team.name; purpose = team.purpose ?: ""; problem = null }
                }
            }
        }
    }
}

/** A member's name and role, and moving them to another team (§37 editBot · moveBot). A team lead stays. */
@Composable
fun BotEditDialog(bot: BotsView.Bot, team: BotsView.Team, onDismiss: () -> Unit) {
    val model = LocalModel.current
    val canEdit = model.capabilities.hasAction("bots", "editBot")
    val targets = if (model.capabilities.hasAction("bots", "moveBot")) BotEdit.moveTargets(model.bots, bot, team) else emptyList()
    var name by remember { mutableStateOf(bot.name) }
    var role by remember { mutableStateOf(bot.role ?: "") }
    var moveTo by remember { mutableStateOf<BotEdit.Target?>(null) }
    var menu by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        val edit = if (canEdit) BotEdit.editBot(bot, name, role) else null
        if (edit is BotEdit.Result.Problem) { problem = edit.text; return }
        val target = moveTo
        if (edit == null && target == null) { onDismiss(); return }
        saving = true; problem = null
        model.scope.launch {
            var why: String? = null
            if (edit is BotEdit.Result.Body) why = sendEdit(model, "editBot", edit.fields)
            if (why == null && target != null) why = sendEdit(model, "moveBot", mapOf("botId" to bot.id, "teamId" to target.id))
            saving = false
            problem = why
            if (why == null) {
                model.toast = if (target != null) "${name.trim()}을(를) ${target.label}(으)로 옮겼어요" else "바꿨어요"
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Theme.ax.card,
        title = { Text("${bot.name} 고치기", style = AxType.headline, color = Theme.ax.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    Text("이름", style = AxType.footnote, color = Theme.ax.secondary)
                    BoxField(name, { name = it }, "이름")
                    Text("역할", style = AxType.footnote, color = Theme.ax.secondary)
                    BoxField(role, { role = it }, "무엇을 맡는지", lines = 3)
                }
                if (bot.rank == "lead") Text("팀장은 다른 팀으로 옮길 수 없어요.", style = AxType.footnote, color = Theme.ax.secondary)
                else if (targets.isNotEmpty()) {
                    Text("팀", style = AxType.footnote, color = Theme.ax.secondary)
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Theme.ax.page).clickable { menu = true }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(moveTo?.label ?: "${team.name} (지금)", style = AxType.body, color = Theme.ax.label, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(16.dp), tint = Theme.ax.secondary)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("${team.name} (지금)") }, onClick = { moveTo = null; menu = false })
                            targets.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { moveTo = t; menu = false }) }
                        }
                    }
                }
                problem?.let { Text(it, style = AxType.footnote, color = SystemColor.red) }
            }
        },
        confirmButton = { TextButton(onClick = { if (!saving) save() }) { Text(if (saving) "저장하는 중…" else "저장", style = AxType.bodyBold, color = Theme.ax.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = AxType.body, color = Theme.ax.secondary) } },
    )
}

/** A rounded text box (one line or a few). */
@Composable
fun BoxField(value: String, onChange: (String) -> Unit, placeholder: String, lines: Int = 1, minHeight: Int = 0) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 40,
        textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(10.dp)).background(Theme.ax.page).padding(12.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = AxType.body, color = Theme.ax.tertiary); inner() } },
    )
}

/**
 * 글로 설명하기 (§37 createTeamFromText): one big box for what the team does, who, the tone, channels, schedule and
 * rules; the Mac builds a name, a purpose and 2–6 members, and keeps the owner's words with the lead.
 */
@Composable
fun TeamFromTextForm(groupId: String?, onOpen: (String) -> Unit) {
    val model = LocalModel.current
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var built by remember { mutableStateOf<TeamFromText.Built?>(null) }
    val done = built
    if (done != null) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = SystemColor.green)
                    Text("팀을 만들었어요", style = AxType.subheadlineBold, color = SystemColor.green)
                }
                CardHeader(done.name, done.purpose.ifBlank { null })
                CardList(done.members) { m -> CardRow(m.name, m.role.ifBlank { null }) }
                if (done.briefKept) Text("적어 주신 설명은 팀장이 늘 지키는 지시로 넣어 뒀어요.", style = AxType.footnote, color = Theme.ax.secondary)
            }
        }
        ActionButton("팀 방 열기", kind = ActionKind.Primary) { onOpen(done.channel) }
        ActionButton("하나 더 만들기") { built = null; text = "" }
        return
    }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("어떤 팀이 필요한지 편하게 적어 주세요. 할 일, 사람, 말투, 채널, 일정, 지켜야 할 것까지 한 번에요.", style = AxType.subheadline, color = Theme.ax.secondary)
            BoxField(text, { text = it; problem = null }, TeamFromText.placeholder, lines = 8, minHeight = 200)
            Text("${text.trim().length} / ${"%,d".format(TeamFromText.MAX)}자", style = AxType.caption, color = Theme.ax.tertiary)
            problem?.let { Text(it, style = AxType.footnote, color = SystemColor.red) }
        }
    }
    ActionButton(if (busy) "팀을 짜는 중… (조금 걸려요)" else "이 설명으로 팀 만들기", kind = ActionKind.Primary, enabled = !busy) {
        TeamFromText.problem(text)?.let { problem = it; return@ActionButton }
        val client = model.client ?: return@ActionButton
        busy = true; problem = null
        model.scope.launch {
            try {
                val body = buildJsonObject { put("text", text.trim()); if (!groupId.isNullOrEmpty()) put("group", groupId) }
                val made = client.viewAction(TeamFromText.Built.serializer(), "bots", "createTeamFromText", body)
                if (!made.isValid) problem = "맥이 만든 팀을 읽지 못했어요. 봇 목록을 새로 고쳐 보세요."
                else { built = made; model.refreshBasics() }
            } catch (e: Exception) { problem = e.friendly } finally { busy = false }
        }
    }
}
