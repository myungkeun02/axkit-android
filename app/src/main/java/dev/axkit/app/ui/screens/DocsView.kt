package dev.axkit.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.CardHeader
import dev.axkit.app.ui.design.CardList
import dev.axkit.app.ui.design.CardRow
import dev.axkit.app.ui.design.Chevron
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.Hairline
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.Loadable
import dev.axkit.app.ui.design.LoadableView
import dev.axkit.app.ui.design.MarkdownDocument
import dev.axkit.app.ui.design.Page
import dev.axkit.app.ui.design.Panel
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.loadInto
import dev.axkit.core.ChatText
import dev.axkit.core.DocGraphView
import dev.axkit.core.DocumentDetail
import dev.axkit.core.DocumentEnvelope
import dev.axkit.core.Format
import dev.axkit.core.KnowledgeSearchView
import dev.axkit.core.KnowledgeView
import dev.axkit.core.KoreanSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the tab last showed, so coming back from a document doesn't load 900+ documents again (a tab's root leaves
 * composition while a pushed screen is up). Kept per connection.
 */
private object DocsMemory {
    var client: Any? = null
    var knowledge: KnowledgeView? = null
    var graph: DocGraphView? = null
    var hits: List<DocHit> = emptyList()
    var hitsFor = ""
    var serverHits: List<DocHit>? = null
    var serverFor = ""

    fun forClient(c: Any?): DocsMemory {
        if (c !== client) { client = c; knowledge = null; graph = null; hits = emptyList(); hitsFor = ""; serverHits = null; serverFor = "" }
        return this
    }
}

/**
 * 문서: a plain tree (분야 → 문서) with the newest on top, and a search that reads titles, 분야, summaries and the
 * documents themselves — spaces ignored, part of a word or first sounds ("ㄱㅈ") enough, hits highlighted.
 */
@Composable
fun DocsView() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val client = model.client
    val memory = DocsMemory.forClient(client)
    var graph by remember(client) { mutableStateOf<Loadable<DocGraphView>>(memory.graph?.let { Loadable.Loaded(it) } ?: Loadable.Loading) }
    var knowledge by remember(client) { mutableStateOf<Loadable<KnowledgeView>>(memory.knowledge?.let { Loadable.Loaded(it) } ?: Loadable.Loading) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val hasGraph = model.capabilities.hasView("docgraph")
    val hasKnowledge = model.capabilities.hasView("knowledge")

    // Built when a list arrives, not on every keystroke in the search box (900+ documents).
    val shelf = remember(knowledge, graph) {
        DocShelf(
            ((knowledge as? Loadable.Loaded)?.value?.let { DocShelf.from(it) } ?: emptyList()) +
                ((graph as? Loadable.Loaded)?.value?.let { DocShelf.from(it) } ?: emptyList()),
        )
    }
    val isLoading = (hasKnowledge && knowledge is Loadable.Loading) || (hasGraph && graph is Loadable.Loading)
    val failure = (knowledge as? Loadable.Failed)?.message ?: (graph as? Loadable.Failed)?.message

    suspend fun loadGraph() {
        val c = client ?: return
        if (!hasGraph) return
        loadInto({ graph = it; (it as? Loadable.Loaded)?.let { l -> memory.graph = l.value } }) { c.view(DocGraphView.serializer(), "docgraph") }
    }
    suspend fun loadKnowledge() {
        val c = client ?: return
        if (!hasKnowledge) return
        loadInto({ knowledge = it; (it as? Loadable.Loaded)?.let { l -> memory.knowledge = l.value } }) { c.view(KnowledgeView.serializer(), "knowledge") }
    }
    suspend fun loadAll() = coroutineScope {
        launch { loadKnowledge() }
        launch { loadGraph() }
    }
    LaunchedEffect(client, hasGraph, hasKnowledge) { loadAll() }

    // axkit://open/docs?q= starts a search; axkit://open/doc?id= opens that document.
    LaunchedEffect(model.docQuery) {
        val q = model.docQuery ?: return@LaunchedEffect
        model.docQuery = null
        nav.popToRoot()
        query = q
        searching = q.isNotEmpty()
    }
    LaunchedEffect(model.docRoute) {
        val id = model.docRoute ?: return@LaunchedEffect
        model.docRoute = null
        val source = shelf.all.firstOrNull { it.id == id }?.source ?: if (hasKnowledge && id.contains("/")) "knowledge" else "docgraph"
        nav.popToRoot()
        val highlight = query
        nav.push("doc") { DocumentScreen(id, source, highlight) }
    }

    Screen("문서", large = true) {
        DocsSearchField(query, { query = it }, searching) { searching = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !hasGraph && !hasKnowledge && model.capabilities.views.isNotEmpty() ->
                    EmptyStateView(SfIcons.of("doc.text.magnifyingglass"), "맥에 아직 문서가 없어요", "맥의 비서를 새 버전으로 올리면 여기에 문서가 보여요.")
                shelf.sections.isEmpty() && isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Theme.ax.secondary, strokeWidth = 2.5.dp)
                    }
                shelf.sections.isEmpty() && failure != null ->
                    EmptyStateView(Icons.Rounded.Warning, "문서를 불러오지 못했어요", failure)
                else -> MoreRefresh({ loadAll() }) { DocShelfView(shelf, query.trim(), memory) }
            }
        }
    }
}

/** The list: newest on top, then 분야 folded open one at a time; with a query, the hits. */
@Composable
private fun DocShelfView(shelf: DocShelf, trimmed: String, memory: DocsMemory) {
    val model = LocalModel.current
    // §21.6: what the Mac found for this query (null: not asked yet, or an older Mac).
    var serverHits by remember { mutableStateOf(if (memory.serverFor == trimmed) memory.serverHits else null) }
    // What was found, worked out away from the screen: scanning 900+ documents (and their bodies) on every
    // keystroke and every body that arrived made typing and scrolling stutter.
    var hits by remember { mutableStateOf(memory.hits) }
    // The query `hits` answers (so "없어요" never shows for a query not searched yet).
    var hitsFor by remember { mutableStateOf(memory.hitsFor) }

    LaunchedEffect(trimmed) {
        if (memory.serverFor != trimmed) serverHits = null
        if (trimmed.isEmpty()) return@LaunchedEffect
        // The Mac searches every document (more than the list holds); the app still reads what it has, for older Macs.
        val client = model.client
        if (serverHits == null && model.capabilities.hasView("knowledge") && client != null) {
            delay(300)
            val found = try {
                client.view(KnowledgeSearchView.serializer(), "knowledge", mapOf("q" to trimmed, "limit" to "50")).results
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            if (found != null) {
                val mapped = found.map { r ->
                    DocHit(
                        DocShelf.Doc(r.id, r.title, null, r.updatedAt, "knowledge", r.areaLabel ?: r.area ?: "문서"),
                        r.snippet?.let { ChatText.preview(it, 120) }, 0,
                    )
                }
                serverHits = mapped
                memory.serverHits = mapped; memory.serverFor = trimmed
            }
        }
        val rest = if (serverHits == null) shelf.all else shelf.all.filter { it.source != "knowledge" }
        model.loadDocBodies(rest.map { Triple(it.key, it.source, it.id) })
    }

    LaunchedEffect(trimmed, serverHits?.size ?: -1, model.docBodies.size, shelf.all.size) {
        if (trimmed.isEmpty()) { hits = emptyList(); hitsFor = ""; return@LaunchedEffect }
        // Bodies arrive in bursts: wait a moment so a burst is searched once.
        if (hits.isNotEmpty()) delay(120)
        val docs = shelf.all
        val bodies = model.docBodies.toMap()
        val fromMac = serverHits.orEmpty()
        val found = withContext(Dispatchers.Default) { DocSearch.findLocal(trimmed, docs, bodies, fromMac) }
        hits = found; hitsFor = trimmed
        memory.hits = found; memory.hitsFor = trimmed
    }

    Page {
        if (trimmed.isEmpty()) DocTree(shelf) else DocResults(shelf, trimmed, hits, hitsFor)
    }
}

@Composable
private fun DocTree(shelf: DocShelf) {
    // Which 분야 are open (kept while a document is up).
    var openKeys by rememberSaveable { mutableStateOf("") }
    val open = openKeys.split("\u0001").filter { it.isNotEmpty() }.toSet()
    val recent = shelf.recent(5)
    if (recent.isNotEmpty()) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardHeader("최근 고친 문서")
                CardList(recent) { DocRow(it, showSection = true) }
            }
        }
    }
    Panel {
        Column(Modifier.animateContentSize()) {
            Box(Modifier.padding(bottom = 6.dp)) { CardHeader("분야별") }
            shelf.sections.forEachIndexed { i, section ->
                val isOpen = section.id in open
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { openKeys = (if (isOpen) open - section.id else open + section.id).joinToString("\u0001") }
                        .semantics { stateDescription = if (isOpen) "펼침" else "접힘" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (isOpen) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, null, Modifier.width(22.dp).size(20.dp), tint = Theme.ax.secondary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(section.label, style = AxType.bodyBold, color = Theme.ax.label)
                        section.summary?.let { Text(it, style = AxType.caption, color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text("${section.docs.size}", style = AxType.subheadline.copy(fontFeatureSettings = "tnum"), color = Theme.ax.secondary)
                    Icon(SfIcons.of(if (isOpen) "chevron.down" else "chevron.right"), null, Modifier.size(16.dp), tint = Theme.ax.tertiary)
                }
                if (isOpen) {
                    val docs = section.docs.sortedByDescending { it.updatedAt ?: java.time.Instant.MIN }
                    Column {
                        docs.forEachIndexed { j, d ->
                            Box(Modifier.padding(start = 32.dp)) { DocRow(d, showSection = false) }
                            if (j < docs.lastIndex) Hairline(Modifier.padding(start = 32.dp))
                        }
                    }
                }
                if (i < shelf.sections.lastIndex) Hairline()
            }
        }
    }
    if (shelf.sections.isEmpty()) {
        EmptyStateView(SfIcons.of("doc.text"), "아직 쌓인 문서가 없어요", "비서와 이야기하면 정한 것과 알게 된 것이 여기에 문서로 쌓여요.")
    }
}

@Composable
private fun DocResults(shelf: DocShelf, trimmed: String, found: List<DocHit>, hitsFor: String) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val reading = model.docBodiesLoading
    when {
        found.isNotEmpty() -> Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardHeader("찾은 문서 ${found.size}개", subtitle = reading?.let { "본문 읽는 중 ${it.first}/${it.second} — 더 나올 수 있어요" })
                CardList(found) { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push("doc") { DocumentScreen(hit.doc.id, hit.doc.source, trimmed) } }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(highlighted(hit.doc.title, trimmed), style = AxType.bodyBold, color = Theme.ax.label)
                            hit.snippet?.let { Text(highlighted(it, trimmed), style = AxType.subheadline, color = Theme.ax.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(
                                buildAnnotatedString {
                                    append(highlighted(hit.doc.section, trimmed))
                                    hit.doc.updatedAt?.let { append(" · ${Format.relative(it)} 고침") }
                                },
                                style = AxType.caption, color = Theme.ax.tertiary,
                            )
                        }
                        Chevron()
                    }
                }
            }
        }
        hitsFor != trimmed -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Theme.ax.secondary, strokeWidth = 2.5.dp)
        }
        reading != null -> EmptyStateView(SfIcons.of("magnifyingglass"), "본문까지 찾는 중이에요", "문서 ${reading.second}개 중 ${reading.first}개를 읽었어요.")
        else -> EmptyStateView(
            SfIcons.of("magnifyingglass"), "‘$trimmed’이(가) 들어간 문서가 없어요",
            "문서 ${shelf.all.size}개의 제목·분야·내용을 모두 찾아봤어요." +
                (if (model.docBodiesFailed > 0) " 내용을 읽지 못한 문서가 ${model.docBodiesFailed}개 있어요." else "") +
                "\n띄어쓰기는 상관없고, 첫소리(예: ㄱㅈ)로도 찾을 수 있어요.",
        )
    }
}

/** The part of `text` that matches, bold on a light yellow mark. */
@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val label = Theme.ax.label
    val r = KoreanSearch.range(text, query) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = label, background = SystemColor.yellow.copy(alpha = 0.35f)), r.first, r.last + 1)
    }
}

@Composable
private fun DocRow(d: DocShelf.Doc, showSection: Boolean) {
    val nav = LocalNav.current
    CardRow(
        d.title,
        detail = listOfNotNull(
            if (showSection) d.section else null,
            d.summary?.let { ChatText.preview(it, 60) },
            if (d.related.isEmpty()) null else "관련: " + d.related.take(3).joinToString(", "),
            d.updatedAt?.let { "${Format.relative(it)} 고침" },
        ).joinToString(" · "),
        modifier = Modifier.clickable { nav.push("doc") { DocumentScreen(d.id, d.source) } },
    ) { Chevron() }
}

/** iOS search bar (`.searchable`): grey rounded field with a magnifier under the title. */
@Composable
private fun DocsSearchField(query: String, onQuery: (String) -> Unit, focused: Boolean, onFocused: (Boolean) -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused) { if (focused) runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.ax.fill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(SfIcons.of("magnifyingglass"), null, Modifier.size(20.dp), tint = Theme.ax.secondary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("제목·내용·분야로 찾기", style = AxType.body, color = Theme.ax.secondary, maxLines = 1)
            BasicTextField(
                query, onQuery,
                textStyle = AxType.body.copy(color = Theme.ax.label),
                singleLine = true,
                cursorBrush = SolidColor(Theme.ax.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { onFocused(it.isFocused) }
                    .semantics { contentDescription = "문서 찾기" },
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Rounded.Cancel, "지우기", Modifier.size(18.dp).clip(CircleShape).clickable { onQuery("") }, tint = Theme.ax.tertiary)
        }
    }
}

object DocText {
    /** The title is already on top: a first heading that repeats it goes. */
    fun withoutTitle(markdown: String, title: String): String {
        val lines = markdown.split("\n").toMutableList()
        while (lines.isNotEmpty() && lines.first().isBlank()) lines.removeAt(0)
        val m = lines.firstOrNull()?.let { Regex("^#{1,3}\\s+(.*)$").find(it) }
        if (m != null && m.groupValues[1].trim() == title.trim()) lines.removeAt(0)
        return lines.joinToString("\n")
    }

    /** Source notes written for the machine ("transcript:35e5….jsonl:667520") read as "대화 기록". */
    fun readable(markdown: String): String = markdown
        .replace(Regex("transcript:[A-Za-z0-9-]+(\\.jsonl)?(:[^\\s)+,]*)?"), "대화 기록")
        .replace(Regex("memory/[A-Za-z0-9._-]+\\.md"), "메모")

    /** The line to mark: where the query sits, else the first line the Mac said matches (§21.6 `matches`). */
    fun foundLine(doc: DocumentDetail, highlight: String): String? {
        val q = highlight.trim()
        if (q.isEmpty()) return null
        KoreanSearch.snippet(doc.markdown, q, width = 120)?.let { return it }
        val line = doc.matches?.firstOrNull()?.line ?: return null
        return doc.markdown.split("\n").getOrNull(line - 1)?.trim()?.replace(Regex("""^[#>*\-•\s]+"""), "")?.replace("**", "")
            ?.ifEmpty { null }?.let { if (it.length > 120) it.take(120) + "…" else it }
    }
}

/**
 * One document: the markdown, the documents tied to it, and a way to ask the assistant to fix it.
 * `source`: which view answers `?doc=` (`docgraph` or `knowledge`, the one the document came from);
 * `highlight`: the search that led here — sent as `q`, and the first matching line is marked.
 */
@Composable
fun DocumentScreen(docId: String, source: String = "docgraph", highlight: String = "") {
    val model = LocalModel.current
    val nav = LocalNav.current
    var state by remember { mutableStateOf<Loadable<DocumentDetail?>>(Loadable.Loading) }
    var asking by remember { mutableStateOf(false) }
    var fix by rememberSaveable { mutableStateOf("") }
    val client = model.client
    val q = highlight.trim()

    suspend fun load() {
        val c = client ?: return
        loadInto({ state = it }) {
            c.view(DocumentEnvelope.serializer(), source, if (q.isEmpty()) mapOf("doc" to docId) else mapOf("doc" to docId, "q" to highlight)).document
        }
    }
    LaunchedEffect(client, docId) { state = Loadable.Loading; load() }

    Screen("") {
        LoadableView(state, retry = { load() }) { doc ->
            if (doc != null) {
                Page {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(doc.title, style = AxType.title2.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
                                doc.updatedAt?.let { Text("${Format.relative(it)} 고침", style = AxType.footnote, color = Theme.ax.secondary) }
                            }
                            val line = remember(doc, q) { DocText.foundLine(doc, q) }
                            if (line != null) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SystemColor.yellow.copy(alpha = 0.18f))
                                        .padding(10.dp)
                                        .semantics(mergeDescendants = true) { contentDescription = "찾은 곳: $line" },
                                    verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(SfIcons.of("magnifyingglass"), null, Modifier.size(18.dp), tint = Theme.ax.accent)
                                    Text(line, style = AxType.subheadline, color = Theme.ax.label)
                                }
                            }
                            val body = remember(doc) { DocText.readable(DocText.withoutTitle(doc.markdown, doc.title)) }
                            MarkdownDocument(body)
                        }
                    }
                    val related = doc.related.orEmpty()
                    if (related.isNotEmpty()) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardHeader("관련 문서")
                                CardList(related) { r ->
                                    CardRow(r.title, modifier = Modifier.clickable { nav.push("doc") { DocumentScreen(r.id, "docgraph") } }) { Chevron() }
                                }
                            }
                        }
                    }
                    ActionButton("틀린 곳이 있으면 비서에게 알려 주기", kind = ActionKind.Secondary) { asking = true }
                }
                if (asking) {
                    AlertDialog(
                        onDismissRequest = { asking = false },
                        containerColor = Theme.ax.card,
                        title = { Text("무엇을 고칠까요?", style = AxType.headline, color = Theme.ax.label) },
                        text = {
                            TextField(
                                fix, { fix = it },
                                placeholder = { Text("예: 투자 목표는 50억이에요", style = AxType.body) },
                                textStyle = AxType.body,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Theme.ax.page, unfocusedContainerColor = Theme.ax.page,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = Theme.ax.accent,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            )
                        },
                        confirmButton = {
                            TextButton({
                                model.send("[문서 고치기: ${doc.title}] $fix", "")
                                fix = ""
                                asking = false
                                model.toast = "비서에게 보냈어요. 고치면 알려 드릴게요."
                            }) { Text("비서에게 보내기", style = AxType.bodyBold, color = Theme.ax.accent) }
                        },
                        dismissButton = {
                            TextButton({ asking = false }) { Text("취소", style = AxType.body, color = Theme.ax.accent) }
                        },
                    )
                }
            } else {
                EmptyStateView(Icons.Rounded.Description, "문서를 찾지 못했어요")
            }
        }
    }
}
