package dev.axkit.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.axkit.core.ApiClient
import dev.axkit.core.ApiError
import dev.axkit.core.AppEvent
import dev.axkit.core.BotsView
import dev.axkit.core.Capabilities
import dev.axkit.core.CardAnswer
import dev.axkit.core.CardEvent
import dev.axkit.core.Choice
import dev.axkit.core.CredentialStore
import dev.axkit.core.EventStreamClient
import dev.axkit.core.Outbox
import dev.axkit.core.PairingLink
import dev.axkit.core.PendingMessage
import dev.axkit.core.PendingRecording
import dev.axkit.core.RecordingQueue
import dev.axkit.core.ServerCredentials
import dev.axkit.core.StreamState
import dev.axkit.core.Timeline
import dev.axkit.core.friendly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class Tab { Home, Approvals, Docs, Calendar, More }

sealed interface Connection {
    object Unpaired : Connection
    object Connecting : Connection
    object Live : Connection
    /** The stream just dropped and is coming back (a short wait); not yet worth a red "can't reach". */
    object Reconnecting : Connection
    data class Offline(val why: String) : Connection
    object Unauthorized : Connection

    val label: String get() = when (this) {
        Unpaired -> "연결 안 됨"
        Connecting -> "연결하는 중"
        Live -> "연결됨"
        Reconnecting -> "다시 연결하는 중"
        is Offline -> "맥에 닿지 않아요"
        Unauthorized -> "다시 연결이 필요해요"
    }

    val color: Color get() = when (this) {
        Live -> Color(0xFF34C759)
        Connecting -> Color(0xFFFF9500)
        Reconnecting -> Color(0xFFFFCC00)
        else -> Color(0xFFFF3B30)
    }
}

/** Everything the screens share: pairing, the live event stream, the timeline and the outbox. Port of AppModel.swift. */
class AppModel(private val context: Context, private val store: CredentialStore) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var credentials by mutableStateOf<ServerCredentials?>(null); private set
    var client by mutableStateOf<ApiClient?>(null); private set
    var capabilities by mutableStateOf(Capabilities()); private set
    var assistantName by mutableStateOf("비서"); private set
    var connection by mutableStateOf<Connection>(Connection.Unpaired); private set
    var stopped by mutableStateOf(false); private set
    var pending by mutableStateOf<List<PendingMessage>>(emptyList()); private set
    var bots by mutableStateOf<BotsView?>(null); private set
    /** §24: the proposed team structure (null = none, or the server has no `team-plan`). */
    var teamPlan by mutableStateOf<dev.axkit.core.TeamPlanView.Plan?>(null)
    /** §7.2 (ax-hq): rooms such as 결재·브리핑·장애·로그 — some read-only. */
    var channels by mutableStateOf<dev.axkit.core.ChannelsView?>(null); private set
    var toast by mutableStateOf<String?>(null)
    var tab by mutableStateOf(Tab.Home)
    /** A room the home tab should open (from a delegation line or a card). */
    var openRoom by mutableStateOf<String?>(null)
    /** True while a sideways drag (the bot sheet) is moving; card choices ignore touches then. */
    var isSwiping by mutableStateOf(false)

    private val dir = context.filesDir

    /** The timeline is a plain mutable object; `version` makes Compose redraw whenever it changes. Kept on disk too. */
    private val timelineStore = dev.axkit.core.TimelineStore(File(dir, "timeline.json"))
    private var version by mutableIntStateOf(0)
    /** Empty until the copy on disk is read (on IO, right after start-up); [loaded] says when. */
    private var replaced by mutableStateOf(Timeline())
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()
    val timeline: Timeline get() { version; return replaced }
    private fun changed() { version++; saveSoon() }

    private var saveJob: kotlinx.coroutines.Job? = null
    /** One writer at a time; a change while writing marks it dirty and it is written again. */
    private val saveLock = kotlinx.coroutines.sync.Mutex()
    private var dirty = false
    /** Writes the timeline a moment after it changes (one write for a burst). */
    private fun saveSoon() {
        dirty = true
        if (saveJob?.isActive == true) return
        saveJob = scope.launch { kotlinx.coroutines.delay(1500); saveNow() }
    }
    private suspend fun saveNow() {
        if (!loaded.isCompleted) return
        saveLock.withLock {
            while (dirty) {
                dirty = false
                // Built here on the main thread (the timeline is not thread-safe), written on IO.
                val chosenCopy = chosen.entries.associate { it.key.toString() to it.value }
                val reactionCopy = reactions.entries.associate { it.key.toString() to it.value }
                val candidates = listOf(150, 60, 20).map { n ->
                    replaced.snapshot(n, skipRoom = ::isHiddenId).copy(chosen = chosenCopy, myReactions = reactionCopy)
                }
                withContext(Dispatchers.IO) { runCatching { timelineStore.save(candidates) } }
            }
        }
    }
    private fun resetTimeline() {
        replaced = Timeline()
        chosen.clear(); reactions.clear()
        changed()
        // Lines sent to the old log will not come back with these seqs.
        scope.launch { outbox.dropSent(); pending = outbox.pending() }
    }

    /** Rooms that are never shown (and never kept on disk or notified). */
    private fun isHiddenId(room: String): Boolean =
        channels?.channels?.firstOrNull { it.id == room }?.let(::isHiddenRoom) ?: (room == "로그")
    private val outbox = Outbox(File(dir, "outbox.json"))
    val recorder = Recorder(context)
    val updater by lazy { AppUpdater(context, scope) }
    val recordings = RecordingQueue(File(dir, "recordings"))
    var pendingRecordings by mutableStateOf<List<PendingRecording>>(emptyList()); private set
    private var uploadingRecordings = false
    private var stream: EventStreamClient? = null
    private var running = false
    /** Bumped by every start/stop, so a start that is still catching up never opens a second stream after a stop. */
    private var generation = 0
    private var startJob: kotlinx.coroutines.Job? = null
    /** True once this run's stream was live, so a later "live" is a reconnect (then capabilities and rooms are read again). */
    private var wasLive = false
    private var retryJob: kotlinx.coroutines.Job? = null
    /** Quick answers the owner picked (message seq → choice id); that row of buttons is then done. Kept on disk. */
    val chosen = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    /** 👍 / 👎 the owner gave a bot's message (seq → emoji). Kept on disk; the Mac keeps the real record. */
    val reactions = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    /** True once capabilities were read in this process (a worker in a fresh process starts without them). */
    var basicsLoaded by mutableStateOf(false); private set
    /** The network the live stream runs on; another default network (or losing this one) means reconnect. */
    private var liveNetwork: android.net.Network? = null
    /** True while the app is in front; new 결재 while away become local notifications. A state: the chat reads only in front. */
    var inFront by mutableStateOf(false)
    /** Goes up with every notice that arrives: the work sheet (§31) reads `views/work` again when nothing runs. */
    var noticesSeen by androidx.compose.runtime.mutableIntStateOf(0); private set

    /**
     * "항상 연결해 두기": a foreground service keeps the stream open while the app is closed, so 결재 and messages
     * notify within seconds instead of on WorkManager's 15-minute (or, in Doze, hours-long) schedule. Off by default.
     */
    var alwaysOn by mutableStateOf(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("alwaysOn", false))
        private set

    fun changeAlwaysOn(on: Boolean) {
        alwaysOn = on
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("alwaysOn", on).apply()
        if (on && isPaired) { AlwaysOnService.start(context); start() } else { AlwaysOnService.stop(context); if (!inFront && !recorder.isRecording) stop() }
    }

    companion object {
        const val MOCK_LINK = "axkit://pair?u=http%3A%2F%2F127.0.0.1%3A47790&n=dev&c=123456"
    }

    init {
        store.load()?.let(::adopt)
        // The copy on disk is read off the main thread; start() and the worker wait for it.
        scope.launch {
            val snap = withContext(Dispatchers.IO) { timelineStore.loadSnapshot() }
            val restored = snap?.let { runCatching { Timeline.restore(it) }.getOrNull() }
            if (snap != null && restored != null && replaced.lastSeq == 0) {
                replaced = restored
                snap.chosen.forEach { (k, v) -> k.toIntOrNull()?.let { chosen[it] = v } }
                snap.myReactions.forEach { (k, v) -> k.toIntOrNull()?.let { reactions[it] = v } }
                version++
            }
            loaded.complete(Unit)
        }
    }

    val isPaired: Boolean get() = credentials != null
    val openCardCount: Int get() = timeline.openCards().size

    private fun adopt(c: ServerCredentials) {
        credentials = c
        client = ApiClient(c)
        connection = Connection.Connecting
    }

    // MARK: lifecycle

    fun start() {
        val client = client ?: return
        if (running) return
        running = true
        wasLive = false
        val gen = ++generation
        startJob = scope.launch {
            loaded.await()
            pending = outbox.pending()
            pendingRecordings = recordings.pending()
            refreshBasics()
            // Catch up page by page first, then stream from where that ended — not both at once from 0.
            catchUp(notify = false)
            if (gen != generation || this@AppModel.client !== client) return@launch
            // After catch-up, so a badge of 0 doesn't clear notifications for cards that are still open.
            Notifier.updateBadge(context, openCardCount)
            val s = EventStreamClient(client)
            stream = s
            s.start(scope, lastSeq = { replaced.lastSeq }, onState = ::streamState, onEvent = ::receive, onActivity = ::receiveActivity)
            refreshWidget()
            flushOutbox()
            uploadRecordings()
        }
    }

    fun stop() {
        // What arrives while leaving was on screen: no notifications for it.
        flushIncoming(notify = false)
        // Activity is not replayed for the past; a new stream sends each busy room once.
        busy.clear()
        running = false
        generation++
        startJob?.cancel(); startJob = null
        retryJob?.cancel(); retryJob = null
        stream?.stop()
        stream = null
        saveJob?.cancel()
        scope.launch { saveNow() }
    }

    /**
     * A default network came up: reconnect now instead of waiting out the backoff, and send what waits. A live stream on
     * this same network is left alone (the callback also fires once when it is registered); a different one (Wi-Fi ↔ LTE)
     * means the old socket is dead even if it has not noticed yet.
     */
    fun networkAvailable(network: android.net.Network) {
        if (!running) return
        if (connection != Connection.Live || (liveNetwork != null && network != liveNetwork)) stream?.reconnect()
        scope.launch { flushOutbox(); uploadRecordings() }
    }

    fun networkLost(network: android.net.Network) {
        if (running && network == liveNetwork) { liveNetwork = null; stream?.reconnect() }
    }

    /** Capabilities, status and rooms are read again at most every 10 seconds (reconnects, rooms the app doesn't know). */
    private val basicsRefresh = Throttle(scope, 10_000)

    suspend fun refreshBasics() {
        val client = client ?: return
        try {
            capabilities = client.capabilities()
            basicsLoaded = true
            val status = client.status()
            // A server whose log started over (new install, wiped data): start the timeline over too, and the stream from 0.
            if (status.lastSeq < replaced.lastSeq) { resetTimeline(); stream?.reconnect() }
            assistantName = status.assistant.name
            stopped = status.stopped ?: false
            if (capabilities.hasView("bots")) bots = runCatching { client.view(BotsView.serializer(), "bots") }.getOrNull()
            reloadTeamPlan()
            if (capabilities.has("channels") && capabilities.hasView("channels")) {
                channels = runCatching { client.view(dev.axkit.core.ChannelsView.serializer(), "channels") }.getOrNull()
                // §21.4: what was read on another device while this phone was away.
                channels?.channels?.forEach { c -> c.readSeq?.let { replaced.setReadUpTo(c.id, it) } }
                changed()
            }
            refreshWidget()
        } catch (e: ApiError) {
            handle(e)
        } catch (e: Exception) {}
    }

    suspend fun reloadBots() {
        val client = client ?: return
        if (capabilities.hasView("bots")) runCatching { client.view(BotsView.serializer(), "bots") }.getOrNull()?.let { bots = it }
        reloadTeamPlan()
    }

    suspend fun reloadTeamPlan() {
        val client = client ?: return
        if (!basicsLoaded) return // not known yet (a worker in a fresh process): leave it as it is
        if (!capabilities.has("team-plan")) { teamPlan = null; return }
        runCatching { dev.axkit.core.TeamPlanView.decode(client.view(kotlinx.serialization.json.JsonElement.serializer(), "team-plan")) }.getOrNull()?.let { teamPlan = it.plan }
    }

    /**
     * Pages of `events` after the timeline's cursor, through the same path as the stream (badge, widget, team plan,
     * activity, bot list, notifications). `notify`: also show notifications (background catch-up).
     */
    private suspend fun catchUp(notify: Boolean = !inFront): Boolean {
        val client = client ?: return false
        repeat(10) {
            val after = replaced.lastSeq
            val page = try { client.events(after = after) } catch (e: ApiError) { handle(e); return false } catch (e: Exception) { return false }
            // The Mac's log started over: start over here too and read it from the beginning.
            if (after > 0 && page.lastSeq < after) { resetTimeline(); return@repeat }
            // The Mac keeps only its newest events; what fell off between `after` and `oldestSeq` can't come back.
            if (after > 0 && page.oldestSeq > after + 1) toast = "오래 떨어져 있어서 그 사이 대화 일부는 이 휴대폰에 없어요. 맥에서는 볼 수 있어요."
            // A fresh phone (nothing kept yet) never turns the whole history into notifications.
            flushIncoming(page.events, notify = notify && after > 0)
            if (page.events.size < 200) return true
        }
        return true
    }

    /** WorkManager, while the app is not in front: new 결재 and assistant messages become notifications. */
    suspend fun backgroundCatchUp() {
        if (inFront || client == null) return
        loaded.await()
        // A fresh process knows nothing yet: names, rooms and features first, so notifications have titles.
        if (!basicsLoaded) refreshBasics()
        pending = outbox.pending()
        catchUp(notify = true)
        flushOutbox()
        saveNow()
    }

    private suspend fun streamState(state: StreamState) {
        when (state) {
            StreamState.Connecting -> if (connection != Connection.Live && connection != Connection.Reconnecting) connection = Connection.Connecting
            StreamState.Live -> {
                connection = Connection.Live
                liveNetwork = context.getSystemService(android.net.ConnectivityManager::class.java)?.activeNetwork
                // A reconnect: the Mac may have changed (new features, rooms, a restarted log).
                if (wasLive) basicsRefresh.request { refreshBasics() }
                wasLive = true
                flushOutbox(); uploadRecordings()
            }
            // A drop or two is shown in yellow; red only when it keeps failing.
            is StreamState.Waiting -> connection = if (state.attempt <= 2) Connection.Reconnecting else Connection.Offline("다시 연결할게요")
            StreamState.Unauthorized -> connection = Connection.Unauthorized
        }
    }

    /**
     * Events from the stream wait here a moment and go into the timeline together, so a burst (a reconnect replaying
     * hundreds) redraws the screens a few times instead of once per event.
     */
    private val incoming = mutableListOf<AppEvent>()
    private var incomingFlush: kotlinx.coroutines.Job? = null

    private fun receive(event: AppEvent) {
        incoming.add(event)
        if (incomingFlush != null) return
        incomingFlush = scope.launch { kotlinx.coroutines.delay(100); flushIncoming() }
    }

    // MARK: activity (§4): "생각하는 중…" under a room's last line

    /** Busy rooms: room → (what it is doing, when we heard). */
    private val busy = androidx.compose.runtime.mutableStateMapOf<String, Pair<dev.axkit.core.Activity, Long>>()
    private var busyTicker: kotlinx.coroutines.Job? = null

    /** What the assistant is doing in a room right now, or null. Old news (3 minutes) is dropped. */
    fun activity(room: String): dev.axkit.core.Activity? =
        busy[room]?.takeIf { System.currentTimeMillis() - it.second < dev.axkit.core.Activity.STALE_MS }?.first

    private fun receiveActivity(a: dev.axkit.core.Activity) {
        if (!capabilities.has("activity")) return
        if (a.isIdle) busy.remove(a.room) else busy[a.room] = a to System.currentTimeMillis()
        if (busy.isNotEmpty() && busyTicker == null) busyTicker = scope.launch {
            while (busy.isNotEmpty()) {
                kotlinx.coroutines.delay(15_000)
                val now = System.currentTimeMillis()
                busy.entries.filter { now - it.value.second >= dev.axkit.core.Activity.STALE_MS }.forEach { busy.remove(it.key) }
            }
            busyTicker = null
        }
    }

    private fun flushIncoming(extra: List<AppEvent> = emptyList(), notify: Boolean = !inFront) {
        incomingFlush?.cancel(); incomingFlush = null
        if (incoming.isEmpty() && extra.isEmpty()) return
        val batch = incoming.toList() + extra; incoming.clear()
        var changedCards = false; var delegated = false; var planCard = false; var unknownRoom = false
        val dayAgo = java.time.Instant.now().minusSeconds(24 * 3600)
        // One notification per room for its newest line (a burst never piles up); each notice on its own.
        val latest = LinkedHashMap<String, dev.axkit.core.MessageEvent>()
        val notices = mutableListOf<dev.axkit.core.NoticeEvent>()
        for (event in batch.sortedBy { it.seq }) {
            if (!replaced.apply(event)) continue
            if (event.channel.isNotEmpty() && (bots != null || channels != null) && !knowsRoom(event.channel)) unknownRoom = true
            when (event) {
                is AppEvent.Card -> {
                    changedCards = true
                    if (notify && replaced.isOpen(event.e.cardId)) Notifier.card(context, event.e)
                    if (dev.axkit.core.TeamPlanEdit.isPlanCard(event.e.topic)) planCard = true
                }
                is AppEvent.CardClosed -> { changedCards = true; Notifier.cancelCard(context, event.e.cardId) }
                is AppEvent.Delegation -> delegated = true
                is AppEvent.Message -> if (!event.e.isOwner) {
                    // The assistant answered in that room: it is no longer thinking there.
                    busy.remove(event.channel)
                    if (notify && event.e.at.isAfter(dayAgo) && notifiesRoom(event.channel) && !isBotTalk(event.e)) latest[event.channel] = event.e
                }
                // Every notice notifies (the Mac already applied quiet hours and its budget); kind only picks how loud.
                is AppEvent.Notice -> {
                    noticesSeen++
                    if (notify && event.e.at.isAfter(dayAgo) && !isHiddenId(event.channel)) notices.add(event.e)
                    if (inFront && event.e.notifies && event.channel != viewingRoom) {
                        banner = Banner(event.channel, roomTitle(event.channel), event.e.text, event.e.isUrgent)
                    }
                }
                // Read on another device: its notification goes.
                is AppEvent.Read -> Notifier.cancelRoom(context, event.channel)
                else -> {}
            }
        }
        changed()
        for ((room, m) in latest) if (replaced.readUpTo(room) < m.seq) {
            Notifier.roomLine(context, room, roomTitle(room), m.author?.name ?: if (room.isEmpty()) assistantName else null, m.text, m.at.toEpochMilli())
        }
        for (n in notices) if (replaced.readUpTo(n.channel ?: "") < n.seq) {
            val room = n.channel ?: ""
            Notifier.notice(context, n.seq, room, n.author?.name ?: roomTitle(room), n.text, urgent = n.notifies, warn = n.isUrgent)
        }
        if (changedCards) { Notifier.updateBadge(context, openCardCount); widgetRefresh.request { refreshWidget() } }
        if (delegated) botsRefresh.request { reloadBots() }
        // A new team-plan question: the bots tab shows its 팀 구조 제안 card.
        if (planCard) scope.launch { reloadTeamPlan() }
        // A room this app has never heard of (a new team, a new bot): read the room list again.
        if (unknownRoom) basicsRefresh.request { refreshBasics() }
        // A sent message whose line has now arrived leaves the outbox (its bubble was kept until now).
        scope.launch { if (outbox.confirm(replaced.lastSeq)) pending = outbox.pending() }
    }

    /** Rooms whose lines notify: not hidden ones, not 팀장회의 (bots talking among themselves). */
    private fun notifiesRoom(room: String): Boolean = !isHiddenId(room) && bots?.groupForMeeting(room) == null

    /** A bot speaking to another bot (R17 `to`, or the "A → B:" form): not for the owner's phone. */
    private fun isBotTalk(m: dev.axkit.core.MessageEvent): Boolean = m.to != null || dev.axkit.core.BotTalk.parse(m.text) != null

    /** An urgent notice or a job's result for another room while the app is open: a banner on top. */
    data class Banner(val room: String, val title: String, val text: String, val urgent: Boolean)
    var banner by mutableStateOf<Banner?>(null)
    /** The room on screen now (set by the chat), so a banner never repeats what the owner is looking at. */
    var viewingRoom: String? = null

    private fun knowsRoom(room: String): Boolean =
        bots?.teamForChannel(room) != null || bots?.groupForMeeting(room) != null || bots?.botForChannel(room) != null ||
            channels?.channels?.any { it.id == room } == true

    /** The widget and the bot list are fetched at most once every 2 seconds, however many events ask for it. */
    private val widgetRefresh = Throttle(scope, 2000)
    private val botsRefresh = Throttle(scope, 2000)

    private fun handle(error: ApiError) {
        when (error) {
            ApiError.Unauthorized -> connection = Connection.Unauthorized
            ApiError.Offline -> connection = Connection.Offline(error.friendlyMessage)
            else -> {}
        }
    }

    // MARK: pairing

    suspend fun pair(raw: String) {
        val link = PairingLink.parse(raw)
        val c = ApiClient.pair(link, android.os.Build.MODEL ?: "안드로이드")
        withContext(Dispatchers.IO) { store.save(c) }
        stop()
        // A new pairing starts clean: nothing typed for the old Mac goes to this one.
        outbox.clear(); pending = emptyList()
        resetTimeline()
        adopt(c)
        CatchUpWorker.schedule(context)
        start()
        dev.axkit.app.signals.PhoneSignals.refresh(context)
    }

    suspend fun unpair() {
        runCatching { client?.unpair() }
        stop()
        CatchUpWorker.cancel(context)
        dev.axkit.app.signals.PhoneSignals.forget(context)
        store.clear()
        outbox.clear()
        credentials = null
        client = null
        bots = null
        teamPlan = null
        channels = null
        pending = emptyList()
        resetTimeline()
        withContext(Dispatchers.IO) { timelineStore.clear() }
        connection = Connection.Unpaired
        capabilities = Capabilities()
        Notifier.updateBadge(context, 0)
        WidgetStore.save(context, null)
    }

    // MARK: chat

    /** `shared`: it came from the system share sheet (§3 `shared:true`). Over 8000 characters it goes in pieces. */
    fun send(text: String, room: String, shared: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val parts = Outbox.split(trimmed)
        if (parts.size > 1) toast = "글이 길어서 ${parts.size}개로 나눠 보낼게요."
        scope.launch {
            for (part in parts) outbox.enqueue(part, room, shared = shared)
            pending = outbox.pending()
            flushOutbox()
        }
    }

    /** A quick answer goes through the outbox too (one clientId, so a retry is never sent twice); its row is then done. */
    fun choose(choice: Choice, room: String, messageSeq: Int) {
        if (chosen[messageSeq] != null) return
        chosen[messageSeq] = choice.id
        saveSoon()
        scope.launch {
            outbox.enqueue(choice.label, room, choice = choice.id, forSeq = messageSeq)
            pending = outbox.pending()
            flushOutbox()
        }
    }

    suspend fun flushOutbox() {
        val client = client ?: return
        if (connection == Connection.Unauthorized) return
        val result = outbox.flush { m ->
            val cardId = m.cardId
            if (cardId != null) sendQueuedAnswer(client, cardId, m.cardBody ?: kotlinx.serialization.json.JsonObject(emptyMap()))
            else client.sendMessage(text = if (m.choice == null) m.text else null, choice = m.choice, channel = m.channel, clientId = m.id, shared = m.shared)
        }
        outbox.confirm(replaced.lastSeq)
        var all = outbox.pending()
        for (id in result.failed) {
            val m = all.firstOrNull { it.id == id } ?: continue
            // A quick answer that failed: its row of buttons can be used again.
            m.forSeq?.let { chosen.remove(it); saveSoon() }
            // A 결재 answer the Mac refused: the card is open again (no bubble for it).
            if (m.cardId != null) {
                replaced.reopenLocally(m.cardId); changed()
                outbox.remove(m.id)
                toast = "결재 답을 맥이 받지 않았어요. ${m.problem ?: ""}".trim()
            }
        }
        all = outbox.pending()
        pending = all
        if (result.dropped.isNotEmpty()) toast = "오래된 인증번호는 늦게 보내지 않았어요."
        if (result.failed.any { id -> all.any { it.id == id && it.cardId == null } }) toast = "보내지 못한 메시지가 있어요. 말풍선을 눌러 다시 보낼 수 있어요."
        if (result.unauthorized) connection = Connection.Unauthorized
        // Busy or unreachable for a moment: try again after a wait that grows (2, 4, 8 … 30 seconds).
        // Only while the app runs; in the background the worker tries again on its own schedule.
        if (result.retryLater && running) {
            val attempts = pending.firstOrNull { it.state == "waiting" }?.attempts ?: 1
            retryJob?.cancel()
            retryJob = scope.launch { kotlinx.coroutines.delay(Outbox.retryDelaySec(attempts) * 1000); flushOutbox() }
        }
        // Without a live stream, read the sent lines back ourselves.
        if (result.sent.isNotEmpty() && connection != Connection.Live) catchUp()
    }

    fun pendingMessages(room: String): List<PendingMessage> = pending.filter { it.channel == room && !it.isCardAnswer }

    /** From a notification's 답하기: queued like anything typed, and tried once before returning. */
    suspend fun sendAndWait(text: String, room: String) {
        for (part in Outbox.split(text.trim())) outbox.enqueue(part, room)
        pending = outbox.pending()
        flushOutbox()
    }

    /** A queued 결재 answer reaches the Mac: close it for real, or open it again when the Mac still wants more. */
    private suspend fun sendQueuedAnswer(client: ApiClient, cardId: String, body: kotlinx.serialization.json.JsonObject): dev.axkit.core.SendResult {
        try {
            val result = client.answerCard(cardId, body)
            when (result.state) {
                "bad_option" -> throw ApiError.BadRequest("option")
                "open" -> { replaced.reopenLocally(cardId); result.reply?.let { toast = it } }
                else -> replaced.closeLocally(cardId, result.state)
            }
        } catch (e: ApiError.Gone) {
            replaced.reopenLocally(cardId); replaced.closeLocally(cardId, "closed")
        }
        changed(); Notifier.updateBadge(context, openCardCount)
        return dev.axkit.core.SendResult(0)
    }

    /** Keeps a 결재 answer for later when the Mac can't be reached; the card shows "맥에 닿으면 답을 보낼게요". */
    private suspend fun queueAnswer(card: CardEvent, body: kotlinx.serialization.json.JsonObject) {
        outbox.enqueueCard(card.cardId, body, card.channel ?: "")
        replaced.closeLocally(card.cardId, "queued"); changed()
        Notifier.updateBadge(context, openCardCount)
        pending = outbox.pending()
    }

    /** "다시 보내기" on a failed bubble. */
    fun resend(id: String) { scope.launch { outbox.retry(id); pending = outbox.pending(); flushOutbox() } }

    /** "지우기" on a failed bubble (its text can be copied first). */
    fun discard(id: String) {
        scope.launch {
            outbox.pending().firstOrNull { it.id == id }?.forSeq?.let { chosen.remove(it); saveSoon() }
            outbox.remove(id); pending = outbox.pending()
        }
    }

    // MARK: recordings (R14)

    val recordingFeature: Boolean get() = capabilities.has("recordings") || capabilities.hasView("recordings")

    fun startRecording() {
        recorder.start(recordings.folder)
        recorder.problem?.let { toast = it; recorder.problem = null }
        if (recorder.isRecording) RecordingService.start(context)
    }

    fun stopRecording() {
        val recording = recorder.stop()
        RecordingService.stop(context)
        if (recording == null) {
            recorder.problem?.let { toast = it; recorder.problem = null }
            return
        }
        scope.launch {
            recordings.add(recording)
            pendingRecordings = recordings.pending()
            toast = "녹음을 맥으로 보낼게요. 정리되면 문서에 생겨요."
            uploadRecordings()
        }
    }

    suspend fun uploadRecordings() {
        val client = client ?: return
        if (uploadingRecordings) return
        uploadingRecordings = true
        try {
            recordings.upload(
                onProgress = { withContext(Dispatchers.Main) { pendingRecordings = it } },
                refused = { r ->
                    withContext(Dispatchers.Main) {
                        val text = "맥이 녹음을 받지 않았어요. ${r.problem ?: ""}".trim()
                        if (inFront) toast = text else Notifier.simple(context, "rec-" + r.id, "녹음을 보내지 못했어요", text, "axkit://open/more?v=recordings")
                    }
                },
            ) { r, i, bytes -> client.uploadRecordingChunk(r, i, bytes) }
            pendingRecordings = recordings.pending()
        } finally {
            uploadingRecordings = false
        }
    }

    fun resendRecording(id: String) { scope.launch { recordings.retry(id); pendingRecordings = recordings.pending(); uploadRecordings() } }
    fun deleteRecording(id: String) { scope.launch { recordings.remove(id); pendingRecordings = recordings.pending() } }

    // MARK: cards

    val cardReplyAllowed: Boolean get() = capabilities.has("card-reply")

    /** Returns true when the card was answered. */
    suspend fun answer(card: CardEvent, answer: CardAnswer, remember: Boolean = false): Boolean {
        val client = client ?: return false
        // §24 "앱에서 고칠게요" (`fix-in-app`): open 팀 구조 here instead of answering; the card stays open for 이대로 만들기.
        if (capabilities.has("team-plan") && answer is CardAnswer.Option && dev.axkit.core.TeamPlanEdit.opensScreen(card.topic, answer.id)) {
            tab = Tab.Home
            openTeamPlan = true
            return false
        }
        // A server without `card-reply`: answer "edit" and send the owner's words to the same room so nothing typed is lost.
        if (answer is CardAnswer.Text && !cardReplyAllowed) {
            return try {
                val result = client.answerCard(card.cardId, CardAnswer.Decision("edit"))
                replaced.closeLocally(card.cardId, result.state, answer.text); changed()
                send("[결재 답: ${card.summary}] ${answer.text}", card.channel ?: "")
                true
            } catch (e: Exception) { toast = e.friendly; false }
        }
        // "앞으로는 알아서" is a standing yes: it only goes with a yes, never with a no or "고쳐서 다시".
        val keep = remember && isApproval(card, answer)
        return try {
            val result = client.answerCard(card.cardId, answer, keep)
            // Not closed: that choice is no longer offered (the card changed).
            if (result.state == "bad_option") { toast = "이 선택지는 지금 고를 수 없어요. 카드를 다시 봐 주세요."; return false }
            if (result.state == "open") {
                // The Mac may re-send this card with more to fill in (a PIN box); only say something if it did.
                result.reply?.let { toast = it }
                return false
            }
            replaced.closeLocally(card.cardId, result.state, (answer as? CardAnswer.Text)?.text); changed()
            if (keep) toast = "앞으로 이런 일은 알아서 할게요" else result.reply?.let { toast = it }
            Notifier.updateBadge(context, openCardCount)
            refreshWidget()
            true
        } catch (e: ApiError.Gone) {
            replaced.closeLocally(card.cardId, "closed"); changed()
            toast = ApiError.Gone.friendlyMessage
            false
        } catch (e: ApiError) {
            // The Mac is out of reach or busy: keep the answer and send it when it is back.
            if (!e.isTransient) { toast = e.friendly; return false }
            queueAnswer(card, client.cardBody(answer, keep))
            toast = "맥에 닿으면 답을 보낼게요."
            true
        } catch (e: Exception) {
            toast = e.friendly
            false
        }
    }

    private fun isApproval(card: CardEvent, answer: CardAnswer): Boolean = when (answer) {
        is CardAnswer.Decision -> answer.value == "approve"
        is CardAnswer.Option -> answer.id == "approve" || answer.id == "yes" ||
            card.options?.firstOrNull { it.id == answer.id }?.toneKind == dev.axkit.core.CardOption.Tone.Primary
        else -> false
    }

    /**
     * From a notification action (승인/거절). Cards without `options` take the classic `decision`; cards with exactly
     * approve/deny options take `option`. Returns false (and says so) when it did not go through.
     */
    suspend fun answerFromNotification(cardId: String, approve: Boolean): Boolean {
        val client = client ?: return false
        loaded.await()
        val card = replaced.card(cardId)
        val value = if (approve) "approve" else "deny"
        val answer = if (card?.options == null) CardAnswer.Decision(value) else CardAnswer.Option(value)
        return try {
            val result = client.answerCard(cardId, answer)
            if (result.state == "bad_option" || result.state == "open") return false
            replaced.closeLocally(cardId, result.state); changed()
            Notifier.updateBadge(context, openCardCount)
            true
        } catch (e: ApiError.Gone) {
            replaced.closeLocally(cardId, "closed"); changed()
            true
        } catch (e: ApiError) {
            if (!e.isTransient || card == null) { toast = e.friendly; return false }
            queueAnswer(card, client.cardBody(answer))
            true
        } catch (e: Exception) {
            toast = e.friendly
            false
        }
    }

    /** Shared from another app (system share sheet): text goes as a message, files through `share`. */
    suspend fun share(text: String?, bytes: ByteArray?, name: String?, contentType: String?): Boolean {
        val client = client ?: return false
        return try {
            if (bytes != null && name != null) client.share(bytes, name, contentType ?: "application/octet-stream")
            else if (!text.isNullOrBlank()) { send(text, "", shared = true); return true }
            true
        } catch (e: Exception) { toast = e.friendly; false }
    }

    // MARK: widget

    /** Writes what the home-screen widget shows, then asks it to redraw. */
    suspend fun refreshWidget() {
        val client = client ?: return
        if (!basicsLoaded) return // features unknown yet: an empty calendar would overwrite the real one
        val today = if (capabilities.hasView("today")) runCatching { client.view(dev.axkit.core.TodayView.serializer(), "today") }.getOrNull() else null
        val events = today?.events.orEmpty().take(4).map {
            WidgetSnapshot.Event(it.title, dev.axkit.core.ISODates.parse(it.start)?.toEpochMilli(), it.allDay == true)
        }
        val open = replaced.openCards()
        withContext(Dispatchers.IO) { WidgetStore.save(context, WidgetSnapshot(System.currentTimeMillis(), open.size, open.firstOrNull()?.summary, events)) }
    }

    // MARK: bot pictures

    /** Place of a name in the bot list (assistant first), so neighbouring bots get different colours and faces. */
    fun characterOrdinal(name: String): Int? {
        if (name == assistantName) return 0
        val index = bots?.allBots?.indexOfFirst { it.name == name } ?: -1
        return if (index >= 0) index + 1 else null
    }

    private val avatarCache = HashMap<String, Bitmap>()

    /** https pictures load directly; `files/<id>` ones need the device token. */
    suspend fun avatarImage(ref: String?): Bitmap? {
        if (ref.isNullOrEmpty()) return null
        avatarCache[ref]?.let { return it }
        val bytes: ByteArray? = when {
            ref.startsWith("files/") -> runCatching { client?.file(ref) }.getOrNull()
            ref.startsWith("https://") -> runCatching {
                withContext(Dispatchers.IO) { java.net.URL(ref).openStream().use { it.readBytes() } }
            }.getOrNull()
            else -> null
        }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return null
        avatarCache[ref] = bitmap
        return bitmap
    }

    suspend fun fileBytes(ref: String): ByteArray? = runCatching { client?.file(ref) }.getOrNull()

    // MARK: rooms

    fun roomTitle(room: String): String {
        if (room.isEmpty()) return assistantName
        bots?.teamForChannel(room)?.let { return it.name }
        bots?.groupForMeeting(room)?.let { return "${it.label} 팀장회의" }
        channels?.channels?.firstOrNull { it.id == room }?.let { return it.label }
        return bots?.botForChannel(room)?.name ?: room
    }

    /** A picture or file from the chat box goes to the assistant (`POST share`, 12 MB at most). */
    val canAttach: Boolean get() = capabilities.has("share")
    var sendingFiles by mutableIntStateOf(0); private set

    fun sendFile(bytes: ByteArray, name: String, contentType: String) {
        val client = client ?: return
        if (bytes.size > 12 * 1024 * 1024) { toast = "12MB보다 큰 파일은 맥에서 직접 넣어 주세요."; return }
        sendingFiles++
        scope.launch {
            try { client.share(bytes, name, contentType) }
            catch (e: Exception) { toast = "${name}을(를) 보내지 못했어요. ${e.friendly}" }
            finally { sendingFiles-- }
        }
    }

    val canReact: Boolean get() = capabilities.has("reactions")

    fun react(seq: Int, emoji: String) {
        val client = client ?: return
        val before = reactions[seq]
        reactions[seq] = emoji
        saveSoon()
        scope.launch {
            try {
                client.react(seq, emoji)
                toast = if (emoji == "👍") "좋았다고 전했어요. 이런 걸 더 할게요." else "별로였다고 전했어요. 이런 건 덜 할게요."
            } catch (e: Exception) {
                if (before == null) reactions.remove(seq) else reactions[seq] = before
                toast = e.friendly
            }
        }
    }

    /** false for read-only rooms (no input box). */
    fun canWrite(room: String): Boolean = channels?.channels?.firstOrNull { it.id == room }?.writable ?: true

    private val readPosts = HashMap<String, kotlinx.coroutines.Job>()

    fun markRead(room: String) {
        val before = replaced.readUpTo(room)
        replaced.markRead(room)
        val upTo = replaced.readUpTo(room)
        Notifier.cancelRoom(context, room)
        if (upTo == before) return
        changed()
        // §21.4: tell the Mac so the other devices clear this room too — once things settle, not for every new line.
        val client = client ?: return
        if (capabilities.has("read-sync")) {
            readPosts[room]?.cancel()
            readPosts[room] = scope.launch { kotlinx.coroutines.delay(1500); runCatching { client.markRead(room, replaced.readUpTo(room)) } }
        }
    }

    // MARK: room list (§21.2)

    data class RoomListItem(
        val id: String, val title: String, val subtitle: String?, val faces: List<String>, val avatar: String? = null,
        val status: String? = null, val chips: List<String> = emptyList(), val last: Pair<String, java.time.Instant>?, val unread: Int,
    )
    data class RoomSection(val id: String, val label: String, val summary: String? = null, val rooms: List<RoomListItem>)

    /**
     * The room list: per company its 팀장회의 and teams (or bots), newest first; then other rooms the owner can write in
     * (개발 세션 …), grouped by the Mac's `group`. Read-only notice rooms show only when they have something in them;
     * 로그 and "알림 방" never. Sections follow the Mac's room order.
     */
    val roomSections: List<RoomSection>
        get() {
            val sections = mutableListOf<RoomSection>()
            val seen = mutableSetOf("")
            fun item(id: String, title: String, subtitle: String?, faces: List<String>, avatar: String? = null, status: String? = null, chips: List<String> = emptyList()): RoomListItem {
                seen.add(id)
                return RoomListItem(id, title, subtitle?.let { dev.axkit.core.ChatText.preview(it, 80) }, faces, avatar, status, chips, roomPreview(id), timeline.unreadCount(id))
            }
            fun newestFirst(rooms: List<RoomListItem>) = rooms.withIndex().sortedWith(compareBy<IndexedValue<RoomListItem>>(
                { it.value.last == null }, { -(it.value.last?.second?.toEpochMilli() ?: 0) }, { it.index },
            )).map { it.value }
            for (group in bots?.groups.orEmpty()) {
                val rooms = mutableListOf<RoomListItem>()
                val leads = group.bots.filter { it.rank == "lead" }
                val meeting = group.meetingChannel
                if (meeting != null && leads.size > 1) rooms.add(item(meeting, "팀장회의", "팀장 ${leads.size}명이 모여 이야기해요", leads.map { it.name }))
                val teams = group.teams
                if (!teams.isNullOrEmpty()) {
                    for (team in teams) if (team.channel !in seen) {
                        val members = bots?.members(team).orEmpty()
                        val lead = members.firstOrNull { it.rank == "lead" }
                        val chips = buildList { if (lead?.isNew == true) add("새 팀"); if (lead?.status == "working") add("일하는 중") }
                        rooms.add(item(team.channel, team.name, lead?.statusText?.let(::statusLine) ?: team.purpose, members.map { it.name }, status = lead?.status, chips = chips))
                    }
                } else {
                    for (bot in group.bots) if (bot.channel !in seen) {
                        val chips = buildList { if (bot.isNew == true) add("새 봇"); if (bot.status == "working") add("일하는 중") }
                        rooms.add(item(bot.channel, roomTitle(bot.channel), bot.statusText?.let(::statusLine) ?: bot.role, listOf(bot.name), bot.avatar, bot.status, chips))
                    }
                }
                sections.add(RoomSection(group.id, group.label, group.summary, newestFirst(rooms)))
            }
            val all = channels?.channels.orEmpty()
            val others = all.filter { c -> c.id !in seen && !isHiddenRoom(c) && (c.writable == true || c.kind == "team" || timeline.items(c.id).isNotEmpty()) }
            val groupOrder = mutableListOf<String>()
            val byGroup = LinkedHashMap<String, MutableList<RoomListItem>>()
            for (c in others) {
                val label = c.group ?: "그 밖의 방"
                if (label !in byGroup) groupOrder.add(label)
                byGroup.getOrPut(label) { mutableListOf() }.add(item(c.id, c.label, if (c.id == "dev-session") "이 맥의 개발 세션과 바로 이야기해요" else null, listOf(c.lead?.name ?: c.label)))
            }
            for (label in groupOrder) sections.add(RoomSection("_g:$label", label, null, newestFirst(byGroup[label].orEmpty())))
            val order = all.withIndex().associate { it.value.id to it.index }
            if (order.isEmpty()) return sections
            return sections.withIndex().sortedWith(compareBy({ s -> s.value.rooms.mapNotNull { order[it.id] }.minOrNull() ?: Int.MAX_VALUE }, { it.index })).map { it.value }
        }

    /** The last line of a room for the list, with who said it: "Leo: …", "Leo → Mia: …", "나: …". */
    fun roomPreview(room: String): Pair<String, java.time.Instant>? {
        for (item in timeline.items(room).asReversed()) {
            when (item) {
                is dev.axkit.core.Timeline.Item.Message -> {
                    val m = item.e
                    val who = if (m.isOwner) "나" else m.author?.name ?: if (room.isEmpty()) assistantName else null
                    val text = dev.axkit.core.ChatText.preview(m.text, 90)
                    if (text.isEmpty()) continue
                    return (who?.let { "$it: $text" } ?: text) to m.at
                }
                is dev.axkit.core.Timeline.Item.Notice -> {
                    val n = item.e
                    dev.axkit.core.BotTalk.parse(n.text)?.let { return "${it.from} → ${it.to}: ${dev.axkit.core.ChatText.preview(it.text, 90)}" to n.at }
                    val text = dev.axkit.core.ChatText.preview(n.text, 90)
                    if (text.isEmpty()) continue
                    return (n.author?.let { "${it.name}: $text" } ?: text) to n.at
                }
                is dev.axkit.core.Timeline.Item.Delegation -> return dev.axkit.core.ChatText.preview(item.e.summary ?: item.e.task, 90) to item.e.at
                is dev.axkit.core.Timeline.Item.Card -> timeline.card(item.id)?.let {
                    return "결재: " + dev.axkit.core.ChatText.preview(dev.axkit.core.ChatText.cardTitle(it.summary, it.note), 80) to it.at
                }
            }
        }
        return null
    }

    /** A screen that needs the whole height (브라우저 화면) hides the floating tab bar, like iOS `.toolbar(.hidden, for: .tabBar)`. */
    var hideTabBar by mutableStateOf(false)
    /** Asks the home tab to push 팀 구조 (§24), e.g. from the chat card's "앱에서 고칠게요". */
    var openTeamPlan by mutableStateOf(false)
    /** Asks the home tab to slide the room list out. */
    var openRoomList by mutableStateOf(false)
    /** The 오늘 sheet on 홈 (a link can open or close it). */
    var showingToday by mutableStateOf(false)
    /** A search to start in 문서 / a document to open there / a 더보기 screen to push. */
    var docQuery by mutableStateOf<String?>(null)
    var docRoute by mutableStateOf<String?>(null)
    var moreRoute by mutableStateOf<String?>(null)

    /** `axkit://open/...` links (widget, notifications). Only opens screens; never acts. */
    fun open(uri: android.net.Uri) {
        showingToday = uri.path == "/today"
        when (uri.path) {
            "/approvals" -> tab = Tab.Approvals
            "/docs" -> { tab = Tab.Docs; docQuery = uri.getQueryParameter("q") }
            "/doc" -> { tab = Tab.Docs; docRoute = uri.getQueryParameter("id") }
            "/calendar" -> tab = Tab.Calendar
            "/room" -> showRoom(uri.getQueryParameter("id") ?: "")
            "/rooms" -> { tab = Tab.Home; openRoomList = true }
            "/team-plan" -> { tab = Tab.Home; openTeamPlan = true }
            "/more" -> { tab = Tab.More; moreRoute = uri.getQueryParameter("v") }
            else -> showRoom("")
        }
    }

    // MARK: document bodies for search (key = source:id), kept for the session

    val docBodies = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var docBodiesLoading by mutableStateOf<Pair<Int, Int>?>(null); private set
    var docBodiesFailed by mutableIntStateOf(0); private set

    /** Reads every document not read yet, six at a time, so search can look inside them. `docs`: (key, source, id). */
    suspend fun loadDocBodies(docs: List<Triple<String, String, String>>) {
        val client = client ?: return
        if (docBodiesLoading != null) return
        val todo = docs.filter { it.first !in docBodies }
        if (todo.isEmpty()) return
        docBodiesLoading = 0 to todo.size
        docBodiesFailed = 0
        try {
            val gate = kotlinx.coroutines.sync.Semaphore(6)
            kotlinx.coroutines.coroutineScope {
                for ((key, source, id) in todo) launch {
                    gate.acquire()
                    try {
                        val body = runCatching { client.view(dev.axkit.core.DocumentEnvelope.serializer(), source, mapOf("doc" to id)).document?.markdown }.getOrNull()
                        if (body != null) docBodies[key] = body else docBodiesFailed++
                        docBodiesLoading = ((docBodiesLoading?.first ?: 0) + 1) to todo.size
                    } finally { gate.release() }
                }
            }
        } finally { docBodiesLoading = null }
    }

    fun showRoom(room: String) {
        tab = Tab.Home
        openRoom = room
    }
}

/** "[AX] [개선] improve… 하는 중" → "improve… 하는 중": tags in brackets mean nothing in a status line. */
fun statusLine(text: String): String = text.replace(Regex("""^(\s*\[[^\]]{1,16}]\s*)+"""), "")

/** Rooms the owner never sees (§21.1–21.2): the old log room and any "알림 방" an older Mac still lists. */
fun isHiddenRoom(c: dev.axkit.core.ChannelsView.Channel): Boolean {
    val name = c.label.replace(" ", "")
    return c.id == "로그" || name == "로그" || name.contains("알림방") || name == "알림"
}

/** Runs a job now, then at most once per interval; requests in between are folded into one run at the end. */
class Throttle(private val scope: kotlinx.coroutines.CoroutineScope, private val intervalMs: Long) {
    private var running = false
    private var again = false
    fun request(job: suspend () -> Unit) {
        if (running) { again = true; return }
        running = true
        scope.launch {
            do { again = false; job(); kotlinx.coroutines.delay(intervalMs) } while (again)
            running = false
        }
    }
}
