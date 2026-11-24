package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BotAvatar
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.friendly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 일하는 방식 (§16): the bot's role prompt, who wrote it, and an editor. The Mac refuses text that would skip 결재. */
@Composable
fun BotInstructionsScreen(botId: String) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    val bot = model.bots?.bot(botId)
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.refreshBasics() }

    val (authorTitle, authorDetail) = when (bot?.instructionsBy) {
        "owner" -> "직접 고친 글" to "대표님이 고친 글이에요. AI가 바꾸지 않아요."
        "ai" -> "AI가 쓴 글" to "팀이 하는 일에 맞춰 AI가 썼어요. 고치면 그 뒤로는 고친 대로 해요."
        else -> "기본 글" to "처음 정해진 글이에요. 고치면 그 뒤로는 고친 대로 해요."
    }

    fun save(value: String?) {
        val client = model.client ?: return
        saving = true
        scope.launch {
            try {
                client.viewAction("bots", "setInstructions", buildJsonObject {
                    put("botId", JsonPrimitive(botId)); put("text", value?.let { JsonPrimitive(it) } ?: JsonNull)
                })
                model.refreshBasics()
                editing = false
                model.toast = if (value == null) "기본 글로 되돌렸어요" else "저장했어요. 다음 일부터 이렇게 해요."
            } catch (e: Exception) {
                // The Mac's own plain reason (400 `message`) shows as-is.
                model.toast = e.friendly
            } finally { saving = false }
        }
    }

    Screen("일하는 방식") {
        MoreRefresh(onRefresh = { model.refreshBasics() }) {
            Page {
                if (bot != null) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotAvatar(bot.name, size = 44.dp, avatar = bot.avatar, status = bot.status)
                            CardHeader(bot.name, listOfNotNull(bot.role, bot.tier?.let { "$it 등급" }).joinToString(" · "))
                        }
                    }
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CardHeader(authorTitle, authorDetail)
                            if (editing) {
                                BasicTextField(
                                    value = text, onValueChange = { text = it },
                                    textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (Theme.ax.dark) Color.Black else Color.White)
                                        .border(1.dp, Theme.ax.separator, RoundedCornerShape(12.dp)).padding(12.dp),
                                    decorationBox = { inner ->
                                        if (text.isEmpty()) Text("이 봇이 어떻게 일하면 좋을지 써 주세요", style = AxType.body, color = Theme.ax.tertiary)
                                        inner()
                                    },
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${text.length} / 1500", style = AxType.caption, color = if (text.length > 1500) SystemColor.red else Theme.ax.secondary)
                                    Text("보내기·결제·삭제는 어떤 글이든 늘 결재를 받아요.", style = AxType.caption, color = Theme.ax.secondary,
                                        modifier = Modifier.weight(1f).padding(start = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                            } else {
                                SelectionContainer {
                                    Text(bot.instructions ?: "아직 정해진 글이 없어요.", style = AxType.body,
                                        color = if (bot.instructions == null) Theme.ax.secondary else Theme.ax.label)
                                }
                            }
                        }
                    }
                    if (editing) {
                        ActionButton(if (saving) "저장하는 중…" else "저장", kind = ActionKind.Primary,
                            enabled = !saving && text.isNotBlank() && text.length <= 1500) { save(text) }
                        ActionButton("취소") { editing = false }
                    } else {
                        ActionButton("고치기", kind = ActionKind.Primary) { text = bot.instructions ?: ""; editing = true }
                        if (bot.instructionsBy == "owner") ActionButton("기본 글로 되돌리기", kind = ActionKind.Danger) { confirmReset = true }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        MoreActionSheet(
            title = "기본 글로 되돌릴까요?",
            message = "직접 고친 글은 사라지고, 다시 AI나 기본 글을 따라요.",
            actions = listOf(MoreAlertButton("되돌리기", MoreAlertButton.Role.Destructive) { save(null) }),
            onDismiss = { confirmReset = false },
        )
    }
}
