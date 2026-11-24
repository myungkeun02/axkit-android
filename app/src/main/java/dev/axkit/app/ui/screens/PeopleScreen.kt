package dev.axkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.Chip
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loaded
import dev.axkit.core.ApiError
import dev.axkit.core.Format
import dev.axkit.core.PeopleView
import dev.axkit.core.PeopleWords
import dev.axkit.core.Person
import dev.axkit.core.PersonEdited
import dev.axkit.core.friendly
import kotlinx.coroutines.launch

/**
 * 더보기 › 사람 (§29 `views/people`): a card for each person the owner told the assistant about. Every room reads them,
 * so "대표님" is never asked again. The owner can fix 직함·회사·관계·메모 here; deleting is done by talking.
 */
@Composable
fun PeopleScreen() {
    val model = LocalModel.current
    var state by remember { mutableStateOf<Loadable<List<Person>>>(Loadable.Loading) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Person?>(null) }

    suspend fun load() {
        val client = model.client ?: return
        try { state = Loadable.Loaded(client.view(PeopleView.serializer(), "people").cards) }
        catch (e: Exception) { if (state !is Loadable.Loaded) state = Loadable.Failed(e.friendly) else model.toast = e.friendly }
    }
    LaunchedEffect(Unit) { load() }

    Screen("사람") {
        LoadableView(state, retry = { load() }) { people ->
            MoreRefresh(onRefresh = { load() }) {
                if (people.isEmpty()) {
                    Page { Box(Modifier.fillMaxWidth().padding(top = 60.dp)) { EmptyStateView(Icons.Rounded.Group, "아직 카드가 없어요", PeopleWords.EMPTY) } }
                } else {
                    val shown = people.filter { PeopleWords.matches(it, query) }
                    Page {
                        PeopleSearchField(query) { query = it }
                        if (shown.isEmpty()) Text("'${query.trim()}'에 맞는 사람이 없어요.", style = AxType.subheadline, color = Theme.ax.secondary, modifier = Modifier.padding(horizontal = 4.dp))
                        shown.forEach { p -> PersonCard(p) { editing = p } }
                        Text(PeopleWords.DELETE_BY_TALKING, style = AxType.footnote, color = Theme.ax.tertiary, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }

    editing?.let { person ->
        PersonEditSheet(person, onDismiss = { editing = null }) { saved ->
            editing = null
            val list = state.loaded ?: return@PersonEditSheet
            state = Loadable.Loaded(listOf(saved) + list.filter { it.key != saved.key })
        }
    }
}

@Composable
private fun PersonCard(p: Person, onEdit: () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { CardHeader(p.name, PeopleWords.roleLine(p).ifEmpty { null }) }
                ActionButton("고치기", fullWidth = false, small = true, onClick = onEdit)
            }
            if (p.alsoCalled.isNotEmpty()) Text("이렇게도 불러요: ${p.alsoCalled.joinToString(", ")}", style = AxType.footnote, color = Theme.ax.tertiary)
            p.relation?.takeIf { it.isNotBlank() }?.let { Text(it, style = AxType.body, color = Theme.ax.label) }
            if (p.noteLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    p.noteLines.forEach { Text("· $it", style = AxType.subheadline, color = Theme.ax.secondary) }
                }
            }
            val meta = listOfNotNull(
                if (p.emails > 0) "메일 주소 ${p.emails}개" else null,
                p.lastSeen?.let { "마지막 연락 ${Format.relative(it)}" },
                p.updatedAt?.let { "${Format.relative(it)} 고침" },
            )
            if (p.openCommitments > 0 || meta.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.openCommitments > 0) Chip("열린 약속 ${p.openCommitments}개", SystemColor.orange)
                    Text(meta.joinToString(" · "), style = AxType.caption, color = Theme.ax.tertiary)
                }
            }
        }
    }
}

@Composable
private fun PeopleSearchField(query: String, onQuery: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Theme.ax.fill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(SfIcons.of("magnifyingglass"), null, Modifier.size(20.dp), tint = Theme.ax.secondary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("이름·회사·관계로 찾기", style = AxType.body, color = Theme.ax.secondary, maxLines = 1)
            BasicTextField(
                query, onQuery, singleLine = true,
                textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "사람 찾기" },
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Rounded.Cancel, "지우기", Modifier.size(18.dp).clip(CircleShape).clickable { onQuery("") }, tint = Theme.ax.tertiary)
    }
}

/** 직함·회사·관계·메모. Only changed boxes go to the Mac; an emptied box clears that line on the card. */
@Composable
private fun PersonEditSheet(person: Person, onDismiss: () -> Unit, onSaved: (Person) -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var form by remember(person) { mutableStateOf(PeopleWords.form(person)) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val body = PeopleWords.editBody(person, form)
    val changed = body.size > 1

    fun save() {
        if (!changed) { onDismiss(); return }
        val client = model.client ?: return
        busy = true; problem = null
        scope.launch {
            try {
                val r = client.viewAction(PersonEdited.serializer(), "people", "edit", body)
                onSaved(r.person ?: PeopleWords.applied(person, body))
            } catch (e: Exception) {
                problem = (e as? ApiError.Refused)?.text ?: e.friendly
                busy = false
            }
        }
    }

    AxSheet(onDismiss) {
        SheetBar(person.name, leading = { BarButton("취소") { onDismiss() } }, trailing = { BarButton(if (busy) "저장 중…" else "저장", bold = true, enabled = changed && !busy) { save() } })
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (f in PeopleWords.FIELDS) {
                MoreFormSection(header = PeopleWords.LABELS[f], footer = if (f == "note") "${PeopleWords.MAX[f]}자까지 · 비운 칸은 카드에서 지워져요." else null) {
                    val v = form[f] ?: ""
                    val max = PeopleWords.MAX[f]!!
                    if (f == "note") NoteField(v, { form = form + (f to it.take(max)) }, PeopleWords.HINTS[f]!!)
                    else MoreField(v, { form = form + (f to it.take(max)) }, PeopleWords.HINTS[f]!!)
                }
            }
            problem?.let { Text(it, style = AxType.footnote, color = SystemColor.red, modifier = Modifier.padding(horizontal = 32.dp)) }
        }
    }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value, onValueChange = onChange, minLines = 3, maxLines = 8,
        textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = AxType.body, color = Theme.ax.tertiary)
                inner()
            }
        },
    )
}
