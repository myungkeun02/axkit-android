package dev.axkit.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.core.CardAnswer
import dev.axkit.core.CardEvent
import dev.axkit.core.CardOption
import dev.axkit.core.CardWords
import kotlinx.coroutines.launch

/** One 결재 card, kept bare: the question, the choices for how to do it, and a text box that is always there. Port of CardPanel.swift. */
@Composable
fun CardPanel(card: CardEvent, compact: Boolean = false, modifier: Modifier = Modifier) {
    val model = LocalModel.current
    val timeline = model.timeline
    val isOpen = timeline.isOpen(card.cardId)
    val closure = timeline.closure(card.cardId)
    val options = card.options?.takeIf { it.isNotEmpty() } ?: CardWords.defaultOptions(card)
    // A card with an input window takes that instead of free text (§7.3).
    val canWrite = card.freeText != false && card.form == null
    // Servers that send only a menu (no buttons) shouldn't get the default approve/deny row.
    val showOptions = !(card.selects?.isNotEmpty() == true && card.options == null)
    var busy by remember(card.cardId) { mutableStateOf<String?>(null) }
    var text by remember(card.cardId) { mutableStateOf("") }
    var remember_ by remember(card.cardId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun send(id: String, answer: CardAnswer) {
        busy = id
        scope.launch {
            val done = model.answer(card, answer, remember = remember_ && id != "text")
            busy = null
            if (done) { text = ""; focus.clearFocus() }
        }
    }

    Column(
        modifier.fillMaxWidth().alpha(if (isOpen) 1f else 0.75f)
            .clip(RoundedCornerShape(Theme.corner)).background(Theme.ax.card)
            .padding(if (compact) 14.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Context first (who asked, what, when, why), then the question.
        // Context is never cut; only leftovers (metadata lines, prompt blocks, Discord marks) are cleaned away.
        val title = remember(card.summary, card.note) { dev.axkit.core.ChatText.cardTitle(card.summary, card.note) }
        val context = remember(card.note) { dev.axkit.core.ChatText.clean(card.note ?: "") }
        if (context.isNotEmpty() && context != title) {
            MarkdownText(
                if (context.startsWith(title + "\n")) context.drop(title.length + 1) else context,
                style = if (compact) AxType.subheadline else AxType.body, color = Theme.ax.secondary,
            )
        }
        AttachedImages(card.attachments, card.imageUrl, if (compact) 160.dp else 260.dp)
        AttachedFiles(card.attachments)
        MarkdownText(title, style = if (compact) AxType.headline else AxType.title3, color = Theme.ax.label)
        val form = card.form
        if (isOpen && form != null) {
            CardFormView(form, busy = busy == "form") { values -> send("form", CardAnswer.Form(form.id, values)) }
        } else if (isOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (select in card.selects.orEmpty()) {
                    CardSelectView(select, busy = busy == "select:" + select.id) { values -> send("select:" + select.id, CardAnswer.Select(select.id, values)) }
                }
                for (option in if (showOptions) options else emptyList()) {
                    val kind = kind(option)
                    // A plain tap only: a drag (scrolling, the bot sheet) that crosses a choice must never pick it.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(kind.background())
                            .semantics { contentDescription = option.label + (option.detail?.let { ", $it" } ?: "") }
                            .safeTap { if (busy == null && !model.isSwiping) send(option.id, CardWords.answer(option)) }
                            .padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, style = AxType.bodyBold, color = kind.foreground(), modifier = Modifier.weight(1f))
                        if (busy == option.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = kind.foreground())
                    }
                }
                val offer = card.remember
                if (offer != null) CheckToggle(remember_, offer.label) { remember_ = it }
                if (canWrite) {
                    InputRow(
                        text = text, onText = { text = it }, placeholder = "다른 지시를 직접 써 주세요",
                        busy = busy == "text", enabled = text.isNotBlank() && busy == null,
                    ) { send("text", CardAnswer.Text(text.trim().take(4000))) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (closure?.state == "denied") Icons.Rounded.Cancel else Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Theme.ax.secondary)
                Text(" " + CardWords.closedLabel(closure, expired = closure == null), style = AxType.subheadlineBold, color = Theme.ax.secondary)
            }
        }
    }
}

/** Same three looks as every button in the app. */
private fun kind(option: CardOption): ActionKind = when (option.toneKind) {
    CardOption.Tone.Primary -> ActionKind.Primary
    CardOption.Tone.Danger -> ActionKind.Danger
    CardOption.Tone.Neutral -> ActionKind.Secondary
}

/** Rounded text box + round ↑ send button (결재 card, chat). */
@Composable
fun InputRow(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    busy: Boolean = false,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSend: () -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = text, onValueChange = onText, maxLines = 5,
            textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (Theme.ax.dark) Color.Black else Color.White)
                .border(1.dp, Theme.ax.separator, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = AxType.body, color = Theme.ax.tertiary)
                inner()
            },
        )
        SendButton(enabled = enabled, busy = busy, label = "이 답 보내기", onClick = onSend)
    }
}

/** The round arrow-up button (grey until there is something to send). */
@Composable
fun SendButton(enabled: Boolean, busy: Boolean = false, label: String = "보내기", size: androidx.compose.ui.unit.Dp = 34.dp, onClick: () -> Unit) {
    if (busy) {
        CircularProgressIndicator(Modifier.padding(4.dp).size(size - 8.dp), strokeWidth = 2.dp, color = Theme.ax.secondary)
        return
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (enabled) Theme.ax.accent else Theme.ax.tertiary)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(size * 0.6f), tint = Color.White) }
}

/** A plain check box: "앞으로는 알아서 하기". */
@Composable
fun CheckToggle(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp).clickable { onChange(!checked) }
            .semantics { stateDescription = if (checked) "선택됨" else "선택 안 됨" },
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(24.dp), tint = if (checked) Theme.ax.accent else Theme.ax.secondary)
        Text(label, style = AxType.subheadline, color = Theme.ax.label, modifier = Modifier.weight(1f).padding(top = 2.dp))
        Spacer(Modifier.size(0.dp))
    }
}

/** §7.3 input window: one field per row; `secret` fields are hidden (a P0 PIN, number pad). Required fields must be filled. */
@Composable
fun CardFormView(form: dev.axkit.core.CardForm, busy: Boolean, submit: (Map<String, String>) -> Unit) {
    val values = androidx.compose.runtime.saveable.rememberSaveable(form.id) { mutableStateOf(form.fields.associate { it.id to (it.value ?: "") }) }
    val ready = form.fields.all { f -> f.required != true || (values.value[f.id] ?: "").isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        form.title?.let { Text(it, style = AxType.subheadlineBold, color = Theme.ax.secondary) }
        for (field in form.fields) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.label + if (field.required == true) "" else " (안 써도 돼요)", style = AxType.footnote, color = Theme.ax.secondary)
                val secret = field.secret == true
                BasicTextField(
                    value = values.value[field.id] ?: "",
                    onValueChange = { values.value = values.value + (field.id to it) },
                    singleLine = field.multiline != true,
                    maxLines = if (field.multiline == true) 8 else 1,
                    textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
                    visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = if (secret) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword, autoCorrectEnabled = false)
                        else androidx.compose.foundation.text.KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (Theme.ax.dark) Color.Black else Color.White)
                        .border(1.dp, Theme.ax.separator, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics { contentDescription = field.label },
                    decorationBox = { inner ->
                        if ((values.value[field.id] ?: "").isEmpty()) Text(field.label, style = AxType.body, color = Theme.ax.tertiary)
                        inner()
                    },
                )
            }
        }
        ActionButton(if (busy) "보내는 중…" else "보내기", kind = ActionKind.Primary, enabled = ready && !busy, safe = true) {
            submit(values.value.filterValues { it.isNotEmpty() })
        }
    }
}

/** §7.3 menu: pick one (or up to `max`), then send. */
@Composable
fun CardSelectView(select: dev.axkit.core.CardSelect, busy: Boolean, submit: (List<String>) -> Unit) {
    var picked by androidx.compose.runtime.saveable.rememberSaveable(select.id) { mutableStateOf(listOf<String>()) }
    val maxCount = maxOf(select.max ?: 1, 1)
    val minCount = maxOf(select.min ?: 1, 0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        select.placeholder?.let {
            Text(it + if (maxCount > 1) " (${maxCount}개까지)" else "", style = AxType.subheadlineBold, color = Theme.ax.secondary)
        }
        for (choice in select.choices) {
            val on = choice.id in picked
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (on) ActionKind.Secondary.background() else Theme.ax.fill)
                    .safeTap {
                        picked = when {
                            on -> picked - choice.id
                            maxCount == 1 -> listOf(choice.id)
                            picked.size < maxCount -> picked + choice.id
                            else -> picked
                        }
                    }
                    .semantics { stateDescription = if (on) "선택됨" else "선택 안 됨" }
                    .padding(12.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    when {
                        maxCount > 1 && on -> Icons.Rounded.CheckBox
                        maxCount > 1 -> Icons.Rounded.CheckBoxOutlineBlank
                        on -> Icons.Rounded.RadioButtonChecked
                        else -> Icons.Rounded.RadioButtonUnchecked
                    },
                    null, Modifier.size(22.dp), tint = if (on) Theme.ax.accent else Theme.ax.secondary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(choice.label, style = AxType.bodyBold, color = Theme.ax.label)
                    choice.description?.let { Text(it, style = AxType.footnote, color = Theme.ax.secondary) }
                }
            }
        }
        ActionButton(
            if (busy) "보내는 중…" else if (maxCount > 1) "고른 ${picked.size}개 보내기" else "이걸로 할게요",
            kind = ActionKind.Primary, enabled = picked.size >= minCount && picked.isNotEmpty() && !busy, safe = true,
        ) { submit(picked) }
    }
}
