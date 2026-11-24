package dev.axkit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import dev.axkit.app.ui.design.LocalAvatarMotion
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.axkit.app.LocalModel
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.AxType
import dev.axkit.app.ui.design.BotAvatar
import dev.axkit.app.ui.design.Chip
import dev.axkit.app.ui.design.GlassButton
import dev.axkit.app.ui.design.LocalNav
import dev.axkit.app.ui.design.NavStack
import dev.axkit.app.ui.design.Screen
import dev.axkit.app.ui.design.Theme
import dev.axkit.core.BotsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** iOS `.snappy`. */
private val snappy = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)

/** Open a room on the home stack (from the bot sheet, a delegation line, a notification). "" = back to the main chat. */
private fun NavStack.showRoom(room: String) {
    popToRoot()
    if (room.isNotEmpty()) push("room:$room") { HomeRoomScreen(room) }
}

/**
 * 홈: the main chat. Like X, sliding the chat to the right pulls a sheet in from the left with the bots, grouped by
 * what the owner does — the sheet sits underneath and the chat slides off it. The list comes from the Mac.
 */
@Composable
fun HomeView() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    /** 0 = closed, 1 = the sheet fully showing. */
    val progress = remember { Animatable(0f) }
    var isOpen by remember { mutableStateOf(false) }
    val canOpen by rememberUpdatedState(nav.routes.isEmpty())

    fun setOpen(open: Boolean) {
        isOpen = open
        scope.launch { progress.animateTo(if (open) 1f else 0f, snappy) }
    }

    LaunchedEffect(model.openRoom) {
        val room = model.openRoom ?: return@LaunchedEffect
        model.openRoom = null
        isOpen = false
        progress.snapTo(0f)
        nav.showRoom(room)
    }
    LaunchedEffect(model.openTeamPlan) {
        if (!model.openTeamPlan) return@LaunchedEffect
        model.openTeamPlan = false
        isOpen = false
        progress.snapTo(0f)
        nav.popToRoot()
        nav.push("team-plan") { TeamPlanScreen() }
    }
    LaunchedEffect(model.openRoomList) {
        if (!model.openRoomList) return@LaunchedEffect
        model.openRoomList = false
        nav.popToRoot()
        setOpen(true)
    }
    BackHandler(enabled = isOpen) { setOpen(false) }

    val unreadInBots = model.roomSections.sumOf { s -> s.rooms.sumOf { it.unread } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = min(maxWidth * 0.84f, 340.dp)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val p = progress.value

        Box(
            Modifier.fillMaxSize().pointerInput(widthPx) {
                val slop = 18.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!canOpen) return@awaitEachGesture
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    val start = if (isOpen) 1f else 0f
                    var total = Offset.Zero
                    var locked = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        total += change.position - change.previousPosition
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!locked) {
                            if (abs(total.x) > slop && abs(total.x) > abs(total.y) * 1.3f) { locked = true; model.isSwiping = true }
                            else if (abs(total.y) > slop) return@awaitEachGesture
                        }
                        if (locked) {
                            change.consume()
                            val value = (start + total.x / widthPx).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(value) }
                        }
                    }
                    if (!locked) return@awaitEachGesture
                    // Let the finger lift fully before card choices take touches again.
                    scope.launch { delay(400); model.isSwiping = false }
                    val drag = total.x
                    val predicted = drag + tracker.calculateVelocity().x * 0.25f
                    val open = when {
                        !isOpen && (drag > widthPx * 0.35f || predicted > widthPx * 0.6f) -> true
                        isOpen && (drag < -widthPx * 0.35f || predicted < -widthPx * 0.6f) -> false
                        else -> isOpen
                    }
                    setOpen(open)
                }
            },
        ) {
            // Underneath: the bot sheet stays put (a little parallax), the chat slides off it like X.
            Box(
                Modifier.width(width).fillMaxHeight()
                    .offset { IntOffset((-widthPx * 0.25f * (1 - p)).toInt(), 0) }
                    .graphicsLayer { alpha = 0.4f + 0.6f * p }
                    .then(if (p == 0f) Modifier.semantics { invisibleToUser() } else Modifier),
            ) {
                // Hidden under the chat: faces stop moving so nobody pays for animation no one sees.
                CompositionLocalProvider(LocalAvatarMotion provides (p > 0f)) {
                    BotDrawer(
                        openPlan = { setOpen(false); nav.push("team-plan") { TeamPlanScreen() } },
                    ) { room ->
                        setOpen(false)
                        if (room.isNotEmpty()) nav.showRoom(room)
                    }
                }
            }

            Box(
                Modifier.fillMaxSize()
                    .offset { IntOffset((p * widthPx).toInt(), 0) }
                    .graphicsLayer { shadowElevation = 12.dp.toPx() * p; shape = RectangleShape; clip = false }
                    .background(Theme.ax.page),
            ) {
                Screen(
                    title = model.assistantName,
                    leading = {
                        val label = "방 목록 열기" + if (unreadInBots > 0) ", 새 글 ${unreadInBots}개" else ""
                        GlassButton(
                            Modifier.semantics(mergeDescendants = true) { contentDescription = label },
                            icon = Icons.Rounded.Group, badge = unreadInBots > 0,
                        ) { setOpen(true) }
                    },
                    trailing = { GlassButton(label = "오늘") { model.showingToday = true } },
                    banner = { StopBanner(); ConnectionBanner() },
                ) {
                    ChatRoomView("", Modifier.weight(1f))
                }
                if (p > 0f) {
                    // Dim the chat that slid aside; tapping it slides it back.
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * p))
                            .pointerInput(Unit) { detectTapGestures { setOpen(false) } }
                            .semantics { invisibleToUser() },
                    )
                }
            }
        }
    }

    if (model.showingToday) AxSheet(onDismiss = { model.showingToday = false }) { TodaySheet(onClose = { model.showingToday = false }) }
}

/** A bot, team or meeting room pushed on the home stack: back chevron, RoomHeader in the middle, the chat. */
@Composable
fun HomeRoomScreen(room: String) {
    val model = LocalModel.current
    val nav = LocalNav.current
    LaunchedEffect(model.openRoom) {
        val target = model.openRoom ?: return@LaunchedEffect
        model.openRoom = null
        nav.showRoom(target)
    }
    Column(Modifier.fillMaxSize().background(Theme.ax.page)) {
        Box(Modifier.fillMaxWidth().background(Theme.ax.page).statusBarsPadding().height(56.dp).padding(horizontal = 12.dp)) {
            Row(
                Modifier.align(Alignment.CenterStart).clickable { nav.pop() }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "뒤로", Modifier.size(20.dp), tint = Theme.ax.accent) }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 56.dp)) { RoomHeader(room) }
        }
        RecordingBar()
        ChatRoomView(room, Modifier.weight(1f))
    }
}

/**
 * The left sheet: the assistant on top, then each company's rooms (팀장회의, each team or bot) newest first like a
 * messenger, each with its last line and an unread dot. Quiet rooms fold away; read-only notice rooms are not shown.
 */
@Composable
fun BotDrawer(openPlan: () -> Unit = {}, open: (String) -> Unit) {
    val model = LocalModel.current
    var addingTeam by remember { mutableStateOf(false) }
    var showQuiet by remember { mutableStateOf(setOf<String>()) }
    val sheet = if (Theme.ax.dark) Color.Black else Color.White
    Box(Modifier.fillMaxSize().background(sheet)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.padding(top = 8.dp).clickable { open("") },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BotAvatar(model.assistantName, size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(model.assistantName, style = AxType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.ax.label)
                    Text(model.bots?.main?.subtitle ?: "무엇이든 먼저 여기에 말하세요", style = AxType.footnote, color = Theme.ax.secondary)
                }
            }

            // §24: the proposed team structure, on top while it waits for the owner.
            val plan = model.teamPlan
            if (plan != null && model.capabilities.has("team-plan")) TeamPlanCard(plan, onOpen = openPlan)

            for (section in model.roomSections) {
                val quiet = section.rooms.filter { it.last == null }
                val active = section.rooms.filter { it.last != null }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.padding(bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
                        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(section.label, style = AxType.headline, color = Theme.ax.label, modifier = Modifier.alignByBaseline())
                        if (!section.summary.isNullOrEmpty()) Text(section.summary, style = AxType.caption, color = Theme.ax.secondary, modifier = Modifier.alignByBaseline())
                    }
                    for (room in active) Box(Modifier.clickable { open(room.id) }) { RoomRow(room) }
                    if (quiet.isNotEmpty()) {
                        val shown = section.id in showQuiet
                        Row(
                            Modifier.fillMaxWidth().clickable { showQuiet = if (shown) showQuiet - section.id else showQuiet + section.id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (shown) "조용한 방 접기" else "아직 이야기가 없는 방 ${quiet.size}개",
                                style = AxType.footnote.copy(fontWeight = FontWeight.Medium), color = Theme.ax.secondary,
                            )
                            Icon(
                                if (shown) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                                Modifier.size(16.dp), tint = Theme.ax.secondary,
                            )
                        }
                        if (shown) for (room in quiet) Box(Modifier.clickable { open(room.id) }) { RoomRow(room) }
                    }
                }
            }
            if (model.capabilities.has("bot-teams")) {
                ActionButton("팀 추가", Modifier.padding(top = 4.dp), kind = ActionKind.Secondary) { addingTeam = true }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).width(0.5.dp).fillMaxHeight().background(Theme.ax.separator))
    }
    if (addingTeam) AddTeamSheet(onDismiss = { addingTeam = false }) { channel -> open(channel) }
}

/** One room in the list: faces, name, a status chip, the last line ("Leo: …") and when, an unread dot. */
@Composable
private fun RoomRow(room: dev.axkit.app.AppModel.RoomListItem) {
    val unread = room.unread > 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).semantics(mergeDescendants = true) {
            if (unread) stateDescription = "새 글 ${room.unread}개"
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(48.dp)) {
            if (room.faces.size > 1) AvatarStack(room.faces, size = 28.dp, working = room.status == "working")
            else BotAvatar(room.faces.firstOrNull() ?: room.title, size = 40.dp, avatar = room.avatar, status = room.status)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        room.title, style = AxType.body.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold),
                        color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    for (chip in room.chips) Chip(chip)
                }
                val at = room.last?.second
                if (at != null) Text(dev.axkit.core.Format.relative(at), style = AxType.caption, color = if (unread) Theme.ax.accent else Theme.ax.secondary)
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.last?.first ?: room.subtitle ?: "", style = AxType.subheadline,
                    color = if (unread) Theme.ax.label else Theme.ax.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(Theme.ax.accent))
            }
        }
    }
}
