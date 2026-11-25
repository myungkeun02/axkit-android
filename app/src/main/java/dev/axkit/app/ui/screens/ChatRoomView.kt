package dev.axkit.app.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Description
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.axkit.app.Connection
import dev.axkit.app.Dictation
import dev.axkit.core.MacDictation
import dev.axkit.core.Mention
import dev.axkit.app.MacDictationRecorder
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.axkit.app.LocalModel
import dev.axkit.app.Tab
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.foreground
import dev.axkit.app.ui.design.AttachedImages
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BotAvatar
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.MarkdownText
import dev.axkit.app.ui.design.SystemColor
import dev.axkit.app.ui.design.Theme
import dev.axkit.app.ui.design.background
import dev.axkit.app.ui.design.safeTap
import dev.axkit.core.CardEvent
import dev.axkit.core.CardWords
import dev.axkit.core.CardOption
import dev.axkit.core.Choice
import dev.axkit.core.DelegationEvent
import dev.axkit.core.Format
import dev.axkit.core.MessageEvent
import dev.axkit.core.NoticeEvent
import dev.axkit.core.Timeline
import dev.axkit.core.RoomFeed
import dev.axkit.core.ChatText
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics

private object BottomMark { override fun toString() = "bottom" }
private object IntroMark { override fun toString() = "intro" }
private object ActivityMark { override fun toString() = "activity" }
private object ReadMark { override fun toString() = "read-line" }

/** Height of the floating tab bar drawn over every tab (RootView.TabBar): 64 + 6 + 6. */
private val TabBarHeight = 76.dp

/** One room: the main chat ("") or a bot's channel. Same look everywhere: a group chat laid out by RoomFeed. */
@Composable
fun ChatRoomView(room: String, modifier: Modifier = Modifier) {
    val model = LocalModel.current
    val items = model.timeline.items(room)
    val pending = model.pendingMessages(room)
    val bot = model.bots?.botForChannel(room)
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()

    // Bottom-first list: index 0 is the bottom mark, so "near the bottom" is the first rows being on screen.
    val atBottom by remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex <= 1 } }
    var unseen by remember(room) { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastCount by remember(room) { androidx.compose.runtime.mutableIntStateOf(-1) }
    var lastPending by remember(room) { androidx.compose.runtime.mutableIntStateOf(0) }
    val count = items.size + pending.size
    val newestSeq = items.lastOrNull()?.seq
    LaunchedEffect(room, count) {
        // Follow new lines only when the owner is already at the bottom or just sent something; else say there are new ones.
        val mine = pending.size > lastPending || (items.lastOrNull() as? Timeline.Item.Message)?.e?.isOwner == true
        if (lastCount < 0 || atBottom || mine) { list.animateScrollToItem(0); unseen = 0 }
        else if (count > lastCount) unseen += count - lastCount
        lastCount = count; lastPending = pending.size
    }
    // Read only once the newest line is on screen.
    // Never while the app is in the background (the screen stays composed there): that line is for a notification.
    LaunchedEffect(room, atBottom, newestSeq, model.inFront) {
        if (atBottom && model.inFront) { unseen = 0; model.markRead(room) }
    }
    val jump = rememberCoroutineScope()

    /** Who speaks for this room when a line has no author: the team lead, else the assistant. */
    val lead = if (room.isNotEmpty() && model.bots?.groupForMeeting(room) == null && bot != null) RoomFeed.Speaker(bot.id, bot.name, bot.avatar) else null
    val feed = rememberRoomFeed(items, room, lead, model.assistantName)
    // Where the owner had read up to when the room opened; the line stays put while they read on.
    val readAtOpen = remember(room) { model.timeline.readUpTo(room) }
    val readLine = remember(feed, readAtOpen) { RoomFeed.readLineIndex(feed, readAtOpen) }
    androidx.compose.runtime.DisposableEffect(room) {
        model.viewingRoom = room
        onDispose { if (model.viewingRoom == room) model.viewingRoom = null }
    }
    val recent = remember(items.size, items.lastOrNull()?.seq) { items.takeLast(2).map { it.seq }.toSet() }
    // The one line whose buttons still work; every other line keeps its buttons, dimmed.
    val live = remember(items.size, items.lastOrNull()?.seq) { dev.axkit.core.ChatChoices.liveSeq(items) }
    var openFolds by rememberSaveable(room) { mutableStateOf(setOf<String>()) }

    // Rows bottom-first: the list grows up from the composer like iOS defaultScrollAnchor(.bottom).
    val activity = model.activity(room)
    val rows: List<Any> = remember(feed, pending, activity != null, readLine) {
        buildList {
            add(BottomMark)
            if (activity != null) add(ActivityMark)
            addAll(pending.asReversed())
            if (readLine == null) addAll(feed.asReversed())
            else {
                addAll(feed.subList(readLine, feed.size).asReversed())
                add(ReadMark)
                addAll(feed.subList(0, readLine).asReversed())
            }
            if (items.isEmpty() && pending.isEmpty()) add(IntroMark)
        }
    }

    Column(modifier.fillMaxSize().background(Theme.ax.page)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = list,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(rows, key = { row ->
                when (row) {
                    is dev.axkit.core.PendingMessage -> "pending-${row.id}"
                    is RoomFeed.Row -> row.id
                    else -> row.toString()
                }
            }, contentType = { it::class }) { row ->
                when (row) {
                    BottomMark -> Spacer(Modifier.height(1.dp))
                    ActivityMark -> model.activity(room)?.let { ActivityLine(it) }
                    ReadMark -> ReadLine()
                    IntroMark -> Box(Modifier.padding(top = 40.dp)) { RoomIntro(room) }
                    is dev.axkit.core.PendingMessage -> PendingBubble(row)
                    is RoomFeed.Row -> FeedRow(row, room, recent, live, isOpen = { it in openFolds }) { id ->
                        openFolds = if (id in openFolds) openFolds - id else openFolds + id
                    }
                }
            }
        }
        if (unseen > 0 && !atBottom) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(CircleShape).background(Theme.ax.accent)
                    .safeTap { jump.launch { list.animateScrollToItem(0) } }.padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "새 메시지 ${unseen}개, 아래로 가기" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("새 메시지 ${unseen}개", style = AxType.footnoteBold, color = Color.White)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp), tint = Color.White)
            }
        }
      }
        if (!model.canWrite(room)) {
            val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Text(
                "이 방은 보기만 해요. 지시는 ${model.assistantName}에게 해 주세요.", style = AxType.footnote, color = Theme.ax.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Theme.ax.page).padding(top = 12.dp, bottom = 12.dp + nav + TabBarHeight),
            )
        } else Composer(
            room,
            placeholder = when {
                model.bots?.groupForMeeting(room) != null -> "팀장들에게 한마디 하기"
                bot != null -> "${bot.name}에게 바로 지시하기"
                else -> "${model.assistantName}에게 무엇이든 말해 보세요"
            },
        )
    }
}

/**
 * The laid-out rows of one room, rebuilt only when the room's items or its speaker change (not on every redraw).
 * The day is in the key so "오늘" turns into "어제" after midnight.
 */
@Composable
private fun rememberRoomFeed(items: List<Timeline.Item>, room: String, lead: RoomFeed.Speaker?, assistant: String): List<RoomFeed.Row> {
    val model = LocalModel.current
    val day = java.time.LocalDate.now(Format.seoul).toEpochDay()
    return remember(room, items.size, items.firstOrNull()?.seq, items.lastOrNull()?.seq, lead, assistant, day) {
        val timeline = model.timeline
        val cardDates = items.filterIsInstance<Timeline.Item.Card>().mapNotNull { c -> timeline.card(c.id)?.let { c.id to it.at } }.toMap()
        // Team and bot rooms show every line in order (no folding); the main room keeps work logs folded.
        RoomFeed.build(items, room, lead, assistant, cardDates, short = room.isEmpty())
    }
}

@Composable
private fun RoomIntro(room: String) {
    val model = LocalModel.current
    val group = model.bots?.groupForMeeting(room)
    val bot = model.bots?.botForChannel(room)
    when {
        group != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarStack(group.bots.filter { it.rank == "lead" }.map { it.name }, size = 44.dp)
            Text("${group.label} 팀장회의", style = AxType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
            Text("팀장들이 모여 이야기하는 방이에요. 여기에 쓰면 다음 회의 주제가 돼요.", style = AxType.footnote, color = Theme.ax.secondary, textAlign = TextAlign.Center)
        }
        bot != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotAvatar(bot.name, size = 88.dp, avatar = bot.avatar, status = bot.status)
            Text(bot.name, style = AxType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
            bot.role?.let { Text(it, style = AxType.body, color = Theme.ax.secondary, textAlign = TextAlign.Center) }
            Text("아직 나눈 이야기가 없어요. 여기에 쓰면 ${bot.name}에게 바로 가요.", style = AxType.footnote, color = Theme.ax.secondary, textAlign = TextAlign.Center)
        }
        room.isEmpty() -> EmptyStateView(
            Icons.Rounded.ChatBubbleOutline, "무엇이든 말해 보세요",
            "예: \"내일 일정 알려 줘\", \"앱 버그 고쳐 줘\", \"원두 주문해 줘\"\n알맞은 봇이 있으면 ${model.assistantName}가 알아서 맡겨요.",
        )
        else -> EmptyStateView(Icons.Rounded.Forum, "아직 조용해요", "새 이야기가 생기면 여기에 보여요.")
    }
}

@Composable
private fun FeedRow(row: RoomFeed.Row, room: String, recent: Set<Int>, live: Int?, isOpen: (String) -> Boolean, toggle: (String) -> Unit) {
    val model = LocalModel.current
    when (row) {
        is RoomFeed.Row.Day -> DayLine(row.label)
        is RoomFeed.Row.EntryRow -> FeedEntry(row.entry, room, showFace = room.isNotEmpty(), showChoices = row.entry.seq in recent, liveSeq = live)
        is RoomFeed.Row.FoldedRow -> FoldedRun(row.folded, room, isOpen(row.folded.id)) { toggle(row.folded.id) }
        is RoomFeed.Row.System -> when (val item = row.item) {
            is Timeline.Item.Notice -> NoticeRow(item.e, room, active = item.seq == live)
            is Timeline.Item.Card -> {
                val card = model.timeline.card(item.id)
                if (card != null && !model.timeline.isDuplicate(item.id)) CardBubble(card)
            }
            is Timeline.Item.Delegation -> DelegationRow(item.e)
            is Timeline.Item.Message -> {}
        }
    }
}

// MARK: composer

@Composable
private fun Composer(room: String, placeholder: String) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var field by rememberSaveable(room, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val draft = field.text
    /** What was typed before talking; spoken words are added after it. */
    var dictationBase by remember { mutableStateOf("") }
    val dictation = remember { Dictation(context.applicationContext) }
    /** §30: when the Mac can write down speech, the mic records here and the Mac's words go in at the cursor. */
    val onMac = model.capabilities.has("dictation")
    val mac = remember(room) {
        MacDictationRecorder(context.applicationContext, scope, { model.client }) { words ->
            val at = MacDictation.insert(field.text, field.selection.min, field.selection.max, words)
            field = TextFieldValue(at.text, TextRange(at.caret))
        }
    }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isEmpty = draft.isBlank()

    fun startDictation() {
        dictation.start()
        dictation.problem?.let { model.toast = it; dictation.problem = null }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) model.toast = if (onMac) MacDictation.MIC_DENIED else Dictation.PERMISSION_PROBLEM
        else if (onMac) mac.start() else startDictation()
    }

    val heard = dictation.heard
    LaunchedEffect(heard) {
        if (!dictation.isListening && heard.isEmpty()) return@LaunchedEffect
        val t = if (dictationBase.isEmpty()) heard else "$dictationBase $heard"
        field = TextFieldValue(t, TextRange(t.length))
    }
    LaunchedEffect(mac.problem) { mac.problem?.let { model.toast = it; mac.problem = null } }
    DisposableEffect(Unit) { onDispose { dictation.stop() } }
    DisposableEffect(mac) { onDispose { mac.cancel() } }

    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom: Dp = max(ime, nav + TabBarHeight)
    val listening = dictation.isListening || mac.phase == MacDictationRecorder.Phase.Recording

    // `@` + a bot's name: the names to offer while the caret is in an `@word`.
    val mention = if (field.selection.collapsed) Mention.query(field.text, field.selection.start) else null
    val botNames = model.bots?.allBots?.sortedBy { if (it.rank == "lead") 0 else 1 }?.map { it.name }.orEmpty() // team leads first
    val offered = mention?.let { Mention.suggest(botNames, it.text) }.orEmpty()

    Column(Modifier.fillMaxWidth().background(Theme.ax.page).padding(bottom = bottom)) {
        WorkStrip()
        if (offered.isNotEmpty() && mention != null) MentionChips(offered) { name ->
            val at = Mention.insert(field.text, mention, name)
            field = TextFieldValue(at.text, TextRange(at.caret))
        }
        if (mac.phase != MacDictationRecorder.Phase.Idle) MacDictationBar(mac)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.canAttach(room) && !listening) AttachButton(room)
            val shape = RoundedCornerShape(20.dp)
            BasicTextField(
                value = field, onValueChange = { field = it }, minLines = 1, maxLines = 6,
                textStyle = AxType.body.copy(color = Theme.ax.label), cursorBrush = SolidColor(Theme.ax.accent),
                modifier = Modifier.weight(1f).focusRequester(focusRequester).clip(shape).background(Theme.ax.card)
                    .border(1.5.dp, if (listening) SystemColor.red.copy(alpha = 0.6f) else Color.Transparent, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("composer"),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text(
                            if (listening) "듣고 있어요… 말씀하세요" else placeholder,
                            style = AxType.body, color = Theme.ax.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        inner()
                    }
                },
            )
            if (onMac) {
                MacMicButton(mac) {
                    if (mac.phase != MacDictationRecorder.Phase.Idle) mac.toggle()
                    else if (mac.hasPermission()) mac.start() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                // Always there (grey when empty) so the mic never moves under the owner's finger: a second tap on the
                // mic to say more must not land on 보내기.
                RoundIcon(Icons.Rounded.ArrowUpward, "보내기", if (isEmpty) Theme.ax.tertiary else Theme.ax.accent, enabled = !isEmpty && mac.phase == MacDictationRecorder.Phase.Idle) {
                    model.send(draft, room)
                    field = TextFieldValue("")
                }
            } else when {
                dictation.isListening -> RoundIcon(Icons.Rounded.Stop, "말로 입력 멈추기", SystemColor.red) { dictation.stop() }
                isEmpty && !model.recorder.isRecording -> RoundIcon(Icons.Rounded.Mic, "말로 입력하기", Theme.ax.accent) {
                    dictationBase = draft
                    focus.clearFocus()
                    if (dictation.hasPermission()) startDictation() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                else -> RoundIcon(Icons.Rounded.ArrowUpward, "보내기", if (isEmpty) Theme.ax.tertiary else Theme.ax.accent, enabled = !isEmpty) {
                    model.send(draft, room)
                    field = TextFieldValue("")
                }
            }
        }
    }
}

/** Bot names for the `@word` at the caret; a tap puts "@이름 " in its place. */
@Composable
private fun MentionChips(names: List<String>, onPick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "봇 이름 고르기" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(names.size) { i ->
            val name = names[i]
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Theme.ax.card).clickable { onPick(name) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dev.axkit.app.ui.design.BotAvatar(name, size = 22.dp)
                Text("@$name", style = AxType.subheadline, color = Theme.ax.label)
            }
        }
    }
}

/** The Mac mic: a mic to start, a red stop while recording, a spinner (tap cancels) while the Mac writes it down. */
@Composable
private fun MacMicButton(mac: MacDictationRecorder, onTap: () -> Unit) {
    val model = LocalModel.current
    when (mac.phase) {
        MacDictationRecorder.Phase.Recording -> RoundIcon(Icons.Rounded.Stop, "받아쓰기 멈추고 맥에 보내기", SystemColor.red, onClick = onTap)
        MacDictationRecorder.Phase.Transcribing -> Box(
            Modifier.padding(bottom = 4.dp).size(36.dp).clip(CircleShape).background(Theme.ax.card).clickable(onClick = onTap)
                .semantics { contentDescription = "받아쓰는 중, 누르면 취소" },
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Theme.ax.accent) }
        MacDictationRecorder.Phase.Idle -> Box(
            Modifier.padding(bottom = 4.dp).size(36.dp).clip(CircleShape)
                .clickable(enabled = !model.recorder.isRecording, onClick = onTap).semantics { contentDescription = "말로 입력하기" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mic, null, Modifier.size(26.dp), tint = if (model.recorder.isRecording) Theme.ax.tertiary else Theme.ax.accent) }
    }
}

/** Above the box while the mic is on: the clock, a loudness meter (so the owner sees it hears them), and 취소. */
@Composable
private fun MacDictationBar(mac: MacDictationRecorder) {
    val recording = mac.phase == MacDictationRecorder.Phase.Recording
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (recording) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(SystemColor.red))
            Text(MacDictation.clock(mac.elapsed.toDouble()), style = AxType.footnote.copy(fontFeatureSettings = "tnum"), color = Theme.ax.label)
            LevelMeter(mac.level, Modifier.weight(1f))
        } else {
            Text("맥에서 글로 바꾸는 중…", style = AxType.footnote, color = Theme.ax.secondary, modifier = Modifier.weight(1f))
        }
        Text("취소", style = AxType.footnote, color = Theme.ax.accent, modifier = Modifier.clickable { mac.cancel() }.padding(4.dp))
    }
}

/** Twelve little bars that fill with the loudness. */
@Composable
private fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val lit = (level * 12).toInt().coerceIn(0, 12)
    Row(modifier.height(14.dp).semantics { contentDescription = "소리 크기" }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(12) { i ->
            Box(
                Modifier.width(5.dp).height((5 + i * 0.8).dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < lit) SystemColor.green else Theme.ax.tertiary.copy(alpha = 0.35f)),
            )
        }
    }
}

/** ＋ next to the main chat box: 사진 보내기 (up to 5, as JPEG under 12 MB) or 파일 보내기. */
@Composable
private fun AttachButton(room: String) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        scope.launch {
            uris.forEachIndexed { i, uri ->
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                } ?: return@forEachIndexed
                val (out, type) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PhotoForSending.make(bytes) }
                model.sendFile(out, "사진-${System.currentTimeMillis() / 1000}-${i + 1}.${if (type == "image/jpeg") "jpg" else "png"}", type, room)
            }
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            for (uri in uris) {
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "파일"
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes == null) { model.toast = "${name}을(를) 열지 못했어요."; continue }
                model.sendFile(bytes, name, context.contentResolver.getType(uri) ?: "application/octet-stream", room)
            }
        }
    }
    Box {
        Box(
            Modifier.padding(bottom = 4.dp).size(36.dp).clip(CircleShape).clickable { menu = true }
                .semantics { contentDescription = if (model.sendingFiles > 0) "보내는 중" else "사진이나 파일 보내기" },
            contentAlignment = Alignment.Center,
        ) {
            if (model.sendingFiles > 0) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Theme.ax.secondary)
            else Icon(Icons.Rounded.AddCircleOutline, null, Modifier.size(28.dp), tint = Theme.ax.secondary)
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("사진 보내기", style = AxType.body) }, leadingIcon = { Icon(Icons.Rounded.Image, null) },
                onClick = { menu = false; photos.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("파일 보내기", style = AxType.body) }, leadingIcon = { Icon(Icons.Rounded.Description, null) },
                onClick = { menu = false; files.launch(arrayOf("*/*")) },
            )
        }
    }
}

/** Photos go as JPEG, made smaller until they fit the Mac's 12 MB limit. */
object PhotoForSending {
    const val LIMIT = 12 * 1024 * 1024
    fun make(data: ByteArray): Pair<ByteArray, String> {
        var image = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size) ?: return data to "image/png"
        fun jpeg(b: android.graphics.Bitmap, q: Int) = java.io.ByteArrayOutputStream().also { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        var out = jpeg(image, 85)
        while (out.size > LIMIT && maxOf(image.width, image.height) > 800) {
            image = android.graphics.Bitmap.createScaledBitmap(image, (image.width * 0.7).toInt(), (image.height * 0.7).toInt(), true)
            out = jpeg(image, 80)
        }
        return out to "image/jpeg"
    }
}

/** iOS "xxx.circle.fill" symbol at 34 pt: a filled circle with a white glyph. */
@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.padding(bottom = 4.dp).size(36.dp).clip(CircleShape).background(color)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = Color.White) }
}

// MARK: bubbles

/** The owner's own line: right side, a light bubble so bots' words stay the focus. */
@Composable
fun OwnerBubble(text: String, footnote: String?, time: java.time.Instant? = null, failed: Boolean = false, faded: Boolean = footnote != null) {
    Column(
        Modifier.fillMaxWidth().padding(start = 48.dp).semantics(mergeDescendants = true) { contentDescription = "나: $text" + (footnote?.let { ", $it" } ?: "") },
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.alpha(if (!faded) 1f else 0.6f).clip(RoundedCornerShape(Theme.corner))
                .background(Theme.ownerBubble).padding(horizontal = 14.dp, vertical = 10.dp),
        ) { ExpandableText(text, limit = 12) }
        if (footnote != null) Text(footnote, style = if (failed) AxType.caption2.copy(fontWeight = FontWeight.SemiBold) else AxType.caption2, color = if (failed) SystemColor.red else Theme.ax.secondary)
        else if (time != null) Text(Format.clock(time), style = AxType.caption2, color = Theme.ax.tertiary)
    }
}

/**
 * A line still on its way: "보내는 중…" / "맥이 연결되면 보낼게요"; once the Mac took it, a plain bubble until its own line
 * arrives. A failed one says so in red; a tap offers 다시 보내기 · 복사 · 지우기.
 */
@Composable
private fun PendingBubble(m: dev.axkit.core.PendingMessage) {
    val model = LocalModel.current
    var menu by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val footnote = when {
        m.isFailed -> "${m.problem ?: "보내지 못했어요"} · 눌러서 다시 보내기"
        m.isSent -> null
        model.connection == Connection.Unauthorized -> "다시 연결하면 보낼게요"
        model.connection == Connection.Live && m.attempts > 0 -> "다시 보내는 중…"
        model.connection == Connection.Live -> "보내는 중…"
        else -> "맥이 연결되면 보낼게요"
    }
    Box(Modifier.fillMaxWidth().then(if (m.isFailed) Modifier.safeTap { menu = true } else Modifier)) {
        OwnerBubble(m.text, footnote, failed = m.isFailed, faded = !m.isSent && !m.isFailed)
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("다시 보내기", style = AxType.body) }, onClick = { menu = false; model.resend(m.id) })
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("복사", style = AxType.body) },
                onClick = { menu = false; clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text)) },
            )
            androidx.compose.material3.DropdownMenuItem(text = { Text("지우기", style = AxType.body, color = SystemColor.red) }, onClick = { menu = false; model.discard(m.id) })
        }
    }
}

/** "여기까지 읽었어요": where the new lines start. */
@Composable
private fun ReadLine() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(Theme.ax.accent.copy(alpha = 0.5f)))
        Text("여기까지 읽었어요", style = AxType.caption.copy(fontWeight = FontWeight.SemiBold), color = Theme.ax.accent)
        Box(Modifier.weight(1f).height(1.dp).background(Theme.ax.accent.copy(alpha = 0.5f)))
    }
}

/** "오늘", "11월 18일 화요일" between days. */
@Composable
fun DayLine(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(0.5.dp).background(Theme.ax.separator))
        Text(label, style = AxType.caption.copy(fontWeight = FontWeight.Medium), color = Theme.ax.secondary, maxLines = 1)
        Box(Modifier.weight(1f).height(0.5.dp).background(Theme.ax.separator))
    }
}

/** Long text shows its first lines and a "더 보기" that opens the rest in place. */
@Composable
fun ExpandableText(text: String, limit: Int = 10, style: androidx.compose.ui.text.TextStyle = AxType.body, color: Color = Theme.ax.label) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    val isLong = remember(text, limit) { text.length > limit * 38 || text.count { it == '\n' } + 1 > limit }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MarkdownText(text, style = style, color = color, maxLines = if (open || !isLong) Int.MAX_VALUE else limit)
        if (isLong) Text(
            if (open) "접기" else "더 보기", style = AxType.footnoteBold, color = Theme.ax.accent,
            modifier = Modifier.clickable { open = !open },
        )
    }
}

/** §22: the errand stopped at a page the owner finishes in the app (not once they said they finished it). */

/** One line in a room: face and name on the first of a run, who it was said to, the bubble, then any replies folded below. */
@Composable
fun FeedEntry(entry: RoomFeed.Entry, room: String, showFace: Boolean, showChoices: Boolean, compact: Boolean = false, liveSeq: Int? = null) {
    val model = LocalModel.current
    if (entry.speaker.isOwner) {
        OwnerBubble(entry.text, null, time = if (entry.showHeader) entry.at else null)
        return
    }
    val face = if (compact) 26.dp else 34.dp
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = if (entry.showHeader) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
    ) {
        if (showFace) {
            if (entry.showHeader) {
                val avatar = entry.speaker.avatar ?: model.bots?.allBots?.firstOrNull { it.name == entry.speaker.name }?.avatar
                BotAvatar(entry.speaker.name, size = face, avatar = avatar)
            } else Spacer(Modifier.width(face))
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (entry.showHeader && (showFace || entry.to != null)) EntryHeader(entry)
            // A notice with an author or in a team room is drawn as a line; its kind still says what it is.
            (entry.source as? Timeline.Item.Notice)?.e?.let { n -> noticeLabel(n)?.let { label ->
                Text(label, style = AxType.footnoteBold, color = if (n.isUrgent) SystemColor.red else Theme.ax.accent)
            } }
            val title = entry.threadTitle
            if (title != null && (entry.replies.isNotEmpty() || entry.thread != null)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Forum, null, Modifier.size(14.dp), tint = Theme.ax.secondary)
                    Text(ChatText.preview(title, 60), style = AxType.footnoteBold, color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Long press: 좋아요 / 별로예요 / 복사 (iOS context menu).
            Box {
                if (entry.text.isNotEmpty()) Row(
                    Modifier.clip(RoundedCornerShape(Theme.corner))
                        .background(when {
                            (entry.source as? Timeline.Item.Notice)?.e?.isUrgent == true -> SystemColor.red.copy(alpha = 0.12f)
                            entry.isStatus -> Theme.statusBubble
                            else -> Theme.assistantBubble
                        })
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(horizontal = 14.dp, vertical = if (entry.isStatus) 8.dp else 10.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(1f, fill = false)) {
                        ExpandableText(
                            entry.text, limit = if (entry.isStatus) 4 else 10,
                            style = if (entry.isStatus) AxType.subheadline else AxType.body,
                            color = if (entry.isStatus) Theme.ax.secondary else Theme.ax.label,
                        )
                    }
                    if (entry.repeats > 1) Text("×${entry.repeats}", style = AxType.captionBold, color = Theme.ax.secondary)
                }
                EntryMenu(menu, entry, onDismiss = { menu = false })
            }
            val counts = model.timeline.reactions(entry.seq)
            if (counts.isNotEmpty()) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "반응 " + counts.entries.joinToString(", ") { "${it.key} ${it.value}개" } },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for ((emoji, n) in counts.entries.sortedByDescending { it.value }) {
                        Text(
                            if (n > 1) "$emoji $n" else emoji, style = AxType.caption, color = Theme.ax.label,
                            modifier = Modifier.clip(CircleShape).background(Theme.ax.card).padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            EntryMedia(entry.source)
            if (model.capabilities.has("browser-live") && ((entry.source as? Timeline.Item.Message)?.e?.let(dev.axkit.core.BrowserLiveWords::opensLive) == true ||
                    (entry.source as? Timeline.Item.Notice)?.e?.live?.view == "browser-live")) {
                val nav = dev.axkit.app.ui.design.LocalNav.current
                dev.axkit.app.ui.design.ActionButton("브라우저 화면 열기", kind = ActionKind.Primary, fullWidth = false, small = true, safe = true) {
                    nav.push("browser-live") { BrowserLiveScreen() }
                }
            }
            if ((entry.source as? Timeline.Item.Notice)?.e?.isWorkResult == true && model.capabilities.hasView("work")) {
                val nav = dev.axkit.app.ui.design.LocalNav.current
                Text("하고 있는 일 보기 ›", style = AxType.footnoteBold, color = Theme.ax.accent,
                    modifier = Modifier.safeTap { nav.push("work") { WorkScreen() } }.padding(vertical = 2.dp))
            }
            if (entry.replies.isNotEmpty()) Replies(entry, room)
            val choices = when (val s = entry.source) {
                is Timeline.Item.Message -> s.e.choices
                is Timeline.Item.Notice -> s.e.choices
                else -> null
            }
            // Buttons stay as a record; only the conversation's live line takes taps (the rest are dimmed).
            if (!choices.isNullOrEmpty()) ChoiceButtons(choices, room, entry.seq, active = entry.seq == liveSeq)
            val source = entry.source
            // The newest bot message shows the two buttons; older ones keep them behind a long press.
            if (source is Timeline.Item.Message && model.canReact && (showChoices || model.reactions[source.seq] != null)) ReactionButtons(source.seq)
        }
        Spacer(Modifier.width(28.dp))
    }
}

@Composable
private fun EntryHeader(entry: RoomFeed.Entry) {
    val to = entry.to
    Row(
        Modifier.clearAndSetSemantics { contentDescription = to?.let { "${Format.subject(entry.speaker.name)} ${it}에게" } ?: entry.speaker.name },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(entry.speaker.name, style = AxType.subheadlineBold, color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (to != null) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(11.dp), tint = Theme.ax.tertiary)
            Text(to, style = AxType.subheadline, color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        entry.label?.let { dev.axkit.app.ui.design.Chip(it) }
        Text(Format.clock(entry.at), style = AxType.caption2, color = Theme.ax.tertiary, maxLines = 1)
    }
}

@Composable
private fun EntryMedia(source: Timeline.Item) {
    when (source) {
        is Timeline.Item.Message -> {
            AttachedImages(source.e.attachments, source.e.imageUrl, height = 180.dp)
            dev.axkit.app.ui.design.AttachedFiles(source.e.attachments)
            LinkList(source.e.links)
        }
        is Timeline.Item.Notice -> {
            AttachedImages(source.e.attachments, source.e.imageUrl, height = 160.dp)
            dev.axkit.app.ui.design.AttachedFiles(source.e.attachments)
            LinkList(source.e.links)
        }
        else -> {}
    }
}

/** "답글 1개 · Mia: 방향은 승인해요…" — tap to read the whole answer under the question. */
@Composable
private fun Replies(entry: RoomFeed.Entry, room: String) {
    var open by rememberSaveable(entry.seq) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (open) for (reply in entry.replies) {
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.padding(vertical = 2.dp).width(2.dp).fillMaxHeight().background(Theme.ax.separator))
                FeedEntry(reply, room, showFace = true, showChoices = false, compact = true)
            }
        }
        Row(
            Modifier.clickable { open = !open }.padding(vertical = 4.dp)
                .semantics { stateDescription = if (open) "답글을 접어요" else "답글을 펼쳐요" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(14.dp), tint = Theme.ax.accent)
            if (open) Text("답글 접기", style = AxType.footnote, color = Theme.ax.accent)
            else {
                Text("답글 ${entry.replies.size}개", style = AxType.footnoteBold, color = Theme.ax.accent)
                entry.replies.lastOrNull()?.let { last ->
                    Text("${last.speaker.name}: ${ChatText.preview(last.text, 40)}", style = AxType.footnote, color = Theme.ax.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Many lines folded into one: "Leo·Mia·Victor의 대화 6개 ›". Opens in place. */
@Composable
private fun FoldedRun(fold: RoomFeed.Folded, room: String, isOpen: Boolean, toggle: () -> Unit) {
    Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Theme.ax.card).clickable(onClick = toggle)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { stateDescription = if (isOpen) "접어요" else "펼쳐서 다 봐요" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarStack(fold.names.take(3), size = 22.dp)
            Text(fold.title, style = AxType.footnoteBold, color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (isOpen) "접기" else "펼치기", style = AxType.footnote, color = Theme.ax.accent)
            Icon(if (isOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp), tint = Theme.ax.accent)
        }
        if (isOpen) for (entry in fold.entries) FeedEntry(entry, room, showFace = true, showChoices = false)
    }
}

/** https links under a message (never opened by themselves). */
@Composable
private fun LinkList(links: List<dev.axkit.core.EventLink>?) {
    val uri = LocalUriHandler.current
    for (link in links.orEmpty().filter { it.url.startsWith("https://") }) {
        Row(
            Modifier.clickable { runCatching { uri.openUri(link.url) } },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Link, null, Modifier.size(16.dp), tint = Theme.ax.accent)
            Text(ChatText.preview(link.label, 40), style = AxType.subheadline, color = Theme.ax.accent)
        }
    }
}

@Composable
private fun EntryMenu(expanded: Boolean, entry: RoomFeed.Entry, onDismiss: () -> Unit) {
    val model = LocalModel.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val message = entry.source as? Timeline.Item.Message
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (message != null && model.canReact) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("좋아요 · 이런 거 더", style = AxType.body) },
                leadingIcon = { Icon(Icons.Outlined.ThumbUp, null) },
                onClick = { onDismiss(); model.react(message.seq, "👍") },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("별로예요 · 이런 거 덜", style = AxType.body) },
                leadingIcon = { Icon(Icons.Outlined.ThumbDown, null) },
                onClick = { onDismiss(); model.react(message.seq, "👎") },
            )
        }
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("복사", style = AxType.body) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            onClick = { onDismiss(); clipboard.setText(androidx.compose.ui.text.AnnotatedString(entry.text)) },
        )
    }
}

/** Two quiet buttons under a bot's message: more like this / less like this. */
@Composable
fun ReactionButtons(seq: Int) {
    val model = LocalModel.current
    val picked = model.reactions[seq]
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("👍", androidx.compose.material.icons.Icons.Outlined.ThumbUp to Icons.Rounded.ThumbUp, "좋아요"),
            Triple("👎", androidx.compose.material.icons.Icons.Outlined.ThumbDown to Icons.Rounded.ThumbDown, "별로예요"),
        ).forEach { (emoji, icons, label) ->
            val on = picked == emoji
            Box(
                Modifier.size(width = 34.dp, height = 28.dp).clip(CircleShape)
                    .background(if (on) dev.axkit.app.ui.design.ActionKind.Secondary.background() else Theme.ax.card)
                    .safeTap { if (!on) model.react(seq, emoji) }
                    .semantics { contentDescription = label; selected = on },
                contentAlignment = Alignment.Center,
            ) { Icon(if (on) icons.second else icons.first, null, Modifier.size(15.dp), tint = if (on) Theme.ax.accent else Theme.ax.secondary) }
        }
    }
}

/** Choices side by side when they fit, else one under another (iOS ViewThatFits). */
@Composable
fun ChoiceButtons(choices: List<Choice>, room: String, messageSeq: Int, active: Boolean = true) {
    val model = LocalModel.current
    val gap = 8.dp
    // One pick per row: after it the row stays as a record (the picked one filled, the rest faded) and takes no taps.
    val picked = model.chosen[messageSeq]
    Layout(content = {
        for (choice in choices) {
            val isPicked = picked == choice.id
            Text(
                choice.label, style = AxType.subheadline.copy(fontWeight = FontWeight.Medium),
                color = if (isPicked) Color.White else Theme.ax.accent,
                modifier = Modifier.alpha(if (isPicked || (picked == null && active)) 1f else 0.4f).clip(CircleShape)
                    .background(if (isPicked) Theme.ax.accent else ActionKind.Secondary.background())
                    .then(if (picked == null && active) Modifier.safeTap { model.choose(choice, room, messageSeq) } else Modifier)
                    .semantics { stateDescription = when { picked != null && isPicked -> "골랐어요"; picked != null -> "고르지 않았어요"; !active -> "지난 버튼이에요"; else -> "" } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val g = gap.roundToPx()
        val rowWidth = placeables.sumOf { it.width } + g * (placeables.size - 1).coerceAtLeast(0)
        if (rowWidth <= constraints.maxWidth) {
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(rowWidth, h) {
                var x = 0
                placeables.forEach { it.placeRelative(x, (h - it.height) / 2); x += it.width + g }
            }
        } else {
            val w = placeables.maxOfOrNull { it.width } ?: 0
            val h = placeables.sumOf { it.height } + g * (placeables.size - 1).coerceAtLeast(0)
            layout(w.coerceAtMost(constraints.maxWidth), h) {
                var y = 0
                placeables.forEach { it.placeRelative(0, y); y += it.height + g }
            }
        }
    }
}

@Composable
fun NoticeRow(notice: NoticeEvent, room: String, active: Boolean) {
    val text = remember(notice.text) { ChatText.clean(notice.text) }
    if (notice.isUrgent || notice.isRoutineResult || notice.isWorkResult) {
        // A seat that opened up, a text that looks like smishing, a routine's result: a card that stands out, not a grey line.
        val urgent = notice.isUrgent
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(Theme.corner))
                .background(if (urgent) SystemColor.red.copy(alpha = 0.10f) else Theme.ax.card)
                .border(if (urgent) 1.dp else 0.dp, if (urgent) SystemColor.red.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(Theme.corner))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                noticeLabel(notice) ?: "",
                style = AxType.footnoteBold, color = if (urgent) SystemColor.red else Theme.ax.accent,
            )
            ExpandableText(text, limit = 8, style = AxType.body, color = Theme.ax.label)
            // §28: an errand that stopped at a page the owner finishes carries the same `live` as an assistant answer.
            if (LocalModel.current.capabilities.has("browser-live") && notice.live?.view == "browser-live") {
                val nav = dev.axkit.app.ui.design.LocalNav.current
                dev.axkit.app.ui.design.ActionButton("브라우저 화면 열기", kind = ActionKind.Primary, fullWidth = false, small = true, safe = true) {
                    nav.push("browser-live") { BrowserLiveScreen() }
                }
            }
            if (notice.isWorkResult && LocalModel.current.capabilities.hasView("work")) {
                val nav = dev.axkit.app.ui.design.LocalNav.current
                Text("하고 있는 일 보기 ›", style = AxType.footnoteBold, color = Theme.ax.accent,
                    modifier = Modifier.safeTap { nav.push("work") { WorkScreen() } }.padding(vertical = 2.dp))
            }
            val choices = notice.choices
            if (!choices.isNullOrEmpty()) ChoiceButtons(choices, room, notice.seq, active)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpandableText(text, limit = 6, style = AxType.footnote.copy(textAlign = TextAlign.Center), color = Theme.ax.secondary)
        val choices = notice.choices
        if (!choices.isNullOrEmpty()) ChoiceButtons(choices, room, notice.seq, active)
    }
}

/** One quiet line: "개발 봇에게 맡겼어요 · 로그인 고치기 ›" — tap to open that bot's room. */
@Composable
fun DelegationRow(event: DelegationEvent) {
    val model = LocalModel.current
    val headline = when (event.state) {
        "done" -> "${Format.subject(event.bot.name)} 끝냈어요"
        "failed" -> "${Format.subject(event.bot.name)} 막혔어요"
        // §21.3: a team lead handed it on (not the assistant): say who.
        else -> event.author?.let { "${Format.subject(it.name)} ${event.bot.name}에게 맡겼어요" } ?: "${event.bot.name}에게 맡겼어요"
    }
    ChatLineCard(
        headline, remember(event.summary, event.task) { ChatText.clean(event.summary ?: event.task) },
        titleColor = if (event.state == "failed") SystemColor.red else Theme.ax.label,
        modifier = Modifier.safeTap { model.showRoom(event.botChannel) },
    )
}

/** A 결재 in the chat is one line; answering happens in the 결재 tab so the conversation stays readable. */
@Composable
fun CardLine(card: CardEvent) {
    val model = LocalModel.current
    val open = model.timeline.isOpen(card.cardId)
    val closure = model.timeline.closure(card.cardId)
    ChatLineCard(
        if (open) "결재 요청" else "결재 끝 · ${CardWords.closedLabel(closure, expired = closure == null)}",
        remember(card) { CardWords.chatLine(card) },
        titleColor = if (open) Theme.ax.accent else Theme.ax.secondary,
        showsChevron = open,
        maxLines = if (open) 8 else 3,
        modifier = Modifier.alpha(if (open) 1f else 0.7f).safeTap { if (open) model.tab = Tab.Approvals },
    )
}

/**
 * A 결재 in the chat reads like the assistant asking: one bubble with the "결재 요청" chip, the question and why, and its
 * buttons inside. Answered, the buttons dim (the picked one stays filled) and the answer shows as the owner's line.
 * A card with a form or a menu is answered in 결재.
 */
@Composable
fun CardBubble(card: CardEvent) {
    val model = LocalModel.current
    val open = model.timeline.isOpen(card.cardId)
    val closure = model.timeline.closure(card.cardId)
    val options = card.options?.takeIf { it.isNotEmpty() } ?: CardWords.defaultOptions(card)
    val inBubble = dev.axkit.core.ChatChoices.answersInBubble(card)
    val answered = remember(card, closure) { dev.axkit.core.ChatChoices.answeredOption(card, closure) }
    val echo = remember(card, closure) { dev.axkit.core.ChatChoices.cardEcho(card, closure) }
    var busy by remember(card.cardId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).clip(RoundedCornerShape(Theme.corner)).background(Theme.assistantBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (open) "결재 요청" else "결재 끝 · ${CardWords.closedLabel(closure, expired = closure == null)}",
                    style = AxType.captionBold, color = if (open) Theme.ax.accent else Theme.ax.secondary,
                    modifier = Modifier.clip(CircleShape).background(if (open) ActionKind.Secondary.background() else Theme.ax.fill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                ExpandableText(remember(card) { CardWords.chatLine(card) }, limit = if (open) 10 else 4, style = AxType.body, color = Theme.ax.label)
                if (inBubble && (card.options != null || card.selects.isNullOrEmpty())) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (option in options) {
                            val picked = answered == option.id
                            val kind = when (option.toneKind) { CardOption.Tone.Primary -> ActionKind.Primary; CardOption.Tone.Danger -> ActionKind.Danger; else -> ActionKind.Secondary }
                            Row(
                                Modifier.alpha(if (open || picked) 1f else 0.4f).clip(CircleShape)
                                    .background(if (!open && picked) Theme.ax.accent else kind.background())
                                    .then(if (open && busy == null) Modifier.safeTap {
                                        if (model.isSwiping) return@safeTap
                                        busy = option.id
                                        scope.launch { model.answer(card, CardWords.answer(option)); busy = null }
                                    } else Modifier)
                                    .semantics { if (!open) stateDescription = if (picked) "골랐어요" else "지난 버튼이에요" }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(option.label, style = AxType.subheadline.copy(fontWeight = FontWeight.Medium), color = if (!open && picked) Color.White else kind.foreground())
                                if (busy == option.id) androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = kind.foreground())
                            }
                        }
                    }
                }
                if (open) Text(
                    if (inBubble) "다른 지시 쓰기 ›" else "결재에서 답하기 ›", style = AxType.footnoteBold, color = Theme.ax.accent,
                    modifier = Modifier.safeTap { model.tab = Tab.Approvals }.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(28.dp))
        }
        if (echo != null) OwnerBubble(echo, null)
    }
}

/** Small white card in the chat for things that aren't messages (맡김, 결재 요청). Tap opens where it points. */
@Composable
fun ChatLineCard(title: String, text: String, titleColor: Color = Theme.ax.label, showsChevron: Boolean = true, maxLines: Int = 3, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Theme.ax.card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = AxType.footnoteBold, color = titleColor)
            Text(text, style = AxType.subheadline, color = Theme.ax.label, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
        if (showsChevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = Theme.ax.tertiary)
    }
}


/** "생각하는 중…" (three dots moving) or the step ("메일 보는 중…") under the room's last line, like a bot bubble. */
@Composable
private fun ActivityLine(activity: dev.axkit.core.Activity) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        0f, 3f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase",
    )
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = activity.words },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(Theme.corner)).background(Theme.ax.card).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) {
                    val lit = phase.toInt() == i
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Theme.ax.secondary.copy(alpha = if (lit) 1f else 0.35f)))
                }
            }
            Text(activity.words, style = AxType.subheadline, color = Theme.ax.secondary)
        }
    }
}

/** The heading a notice's kind gets (null: a plain notice). */
fun noticeLabel(n: NoticeEvent): String? = when (n.kind) {
    "phone.smishing" -> "⚠️ 스미싱 의심"
    "errands.seat" -> "빈자리가 났어요"
    "dev.job" -> "개발 작업 결과"
    "browser.task" -> "브라우저 심부름 결과"
    "errands.task" -> "생활 심부름 결과"
    "work.interrupted" -> "중간에 멈춘 일"
    "routines.result" -> "루틴 결과"
    "overnight" -> "밤사이 소식"
    else -> null
}
