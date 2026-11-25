package dev.axkit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * 휴대폰 신호: what the phone sees (notices, texts, where it is, battery, steps) sent to the paired Mac's
 * `POST /m/v1/ingest` (ax-kit `src/mobile/schema.ts`). The Mac does the rest: card texts → 가계부, codes → the browser's
 * sign-in, parcels and bookings → 할 일, location → lunch, travel time and weather. This replaces MacroDroid.
 */
enum class SignalKind(val wire: String, val title: String) {
    Notification("notification", "앱 알림"),
    Sms("sms", "문자"),
    Location("location", "위치"),
    Battery("battery", "배터리"),
    Steps("steps", "걸음 수");

    companion object { fun of(wire: String) = entries.firstOrNull { it.wire == wire } }
}

/** One thing to send. `at` is when it happened (the Mac takes items up to 48 hours old, so a queue works offline). */
@Serializable
data class SignalItem(
    val kind: String,
    val at: Long,
    val id: String = UUID.randomUUID().toString(),
    val app: String? = null,
    val title: String? = null,
    val from: String? = null,
    val text: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val label: String? = null,
    val accuracy: Double? = null,
    val level: Int? = null,
    val steps: Long? = null,
    val date: String? = null,
) {
    /** The wire shape: only the fields the Mac's schema knows for this kind. */
    fun wire(): JsonObject = buildJsonObject {
        put("kind", kind)
        put("at", Instant.ofEpochMilli(at).toString())
        when (kind) {
            "notification" -> { put("app", app.orEmpty().take(200)); title?.let { put("title", it.take(200)) }; put("text", text.orEmpty().take(200)) }
            "sms" -> { from?.let { put("from", it.take(200)) }; put("text", text.orEmpty().take(1000)) }
            "location" -> { put("lat", lat ?: 0.0); put("lng", lng ?: 0.0); label?.let { put("label", it.take(200)) }; accuracy?.let { put("accuracy", it.coerceIn(0.0, 100_000.0)) } }
            "battery" -> put("level", (level ?: 0).coerceIn(0, 100))
            "steps" -> { put("steps", (steps ?: 0).coerceIn(0, 200_000)); date?.let { put("date", it) } }
        }
    }

    /** What the Mac would refuse (and with it the whole batch): empty text, a bad place, a missing app name. */
    val isSendable: Boolean get() = when (kind) {
        "notification" -> !app.isNullOrBlank() && !text.isNullOrBlank()
        "sms" -> !text.isNullOrBlank()
        "location" -> lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
        "battery" -> level != null && level in 0..100
        "steps" -> steps != null && steps in 0..200_000 && (date == null || Regex("""\d{4}-\d{2}-\d{2}""").matches(date))
        else -> false
    }
}

object Signals {
    const val BATCH = 50
    /** The Mac takes up to 48 hours; keep a margin for a slow clock. */
    const val MAX_AGE_MS = 47L * 60 * 60_000
    /** A phone offline for two days does not grow the file without end. */
    const val MAX_QUEUED = 1000

    /** `{"v":1,"ts":…,"items":[…]}` — `ts` is the send time (the Mac checks it is within five minutes of its own). */
    fun body(items: List<SignalItem>, now: Long): JsonObject = buildJsonObject {
        put("v", 1)
        put("ts", Instant.ofEpochMilli(now).toString())
        put("items", JsonArray(items.map { it.wire() }))
    }

    /** `{ok:true, stored}` → stored count; `{ok:false, problem}` (an empty text the Mac explains) → null. */
    fun stored(body: String): Int? {
        val obj = runCatching { ProtocolJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        return obj["stored"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    }

    // MARK: notifications

    /** Text messages: the phone's own messaging apps (plus whatever the default SMS app is). */
    val messagingApps = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
        "com.lge.message", "com.sec.mms",
    )

    /** On by default: KakaoTalk, card, bank and payment apps (Korea). Anything else the owner picks. */
    val defaultApps = setOf(
        "com.kakao.talk",
        // 간편결제·인터넷은행
        "viva.republica.toss", "com.kakaopay.app", "com.naverfin.payapp", "com.samsung.android.spay", "com.kakaobank.channel",
        "com.kbankwith.smartbank", "com.tossbank.app",
        // 카드
        "com.shcard.smartpay", "kr.co.samsungcard.mpocket", "com.hyundaicard.appcard", "com.kbcard.cxh.appcard", "com.lcacApp",
        "com.wooricard.smartapp", "com.hanaskcard.paycla", "nh.smart.nhallonepay", "kvp.jjy.MispAndroid320", "com.bccard.paybooc",
        "com.lottemembers.android",
        // 은행
        "com.kbstar.kbbank", "com.kbstar.liivbank", "com.shinhan.sbanking", "com.wooribank.smart.npib", "com.kebhana.hanapush",
        "nh.smart.banking", "com.nonghyup.nhallonebank", "com.ibk.android.ionebank", "com.epost.psf.sdsi", "com.knb.psb",
        "com.dgb.mobilebank", "kr.co.busanbank.mbp", "com.scbank.ma30", "com.citibank.cityplus", "com.kdb.mobilebank",
        "com.suhyup.pesmb", "kr.co.kfcc.mobilebank", "com.cu.sb",
    )

    private val moneyWords = listOf("카드", "뱅크", "은행", "페이", "증권", "결제", "pay", "bank", "card", "wallet")

    /** Default on: a known app, a messaging app, or one whose name reads like money (신한카드, 우리은행, ○○페이…). */
    fun isDefaultApp(packageName: String, label: String, smsApp: String? = null): Boolean {
        if (packageName in defaultApps || packageName in messagingApps || packageName == smsApp) return true
        val name = label.lowercase()
        return moneyWords.any { name.contains(it) }
    }

    fun isMessaging(packageName: String, smsApp: String?) = packageName in messagingApps || packageName == smsApp

    /**
     * The Mac reads one line: the expanded text if there is one, else the short one; a chat shows its last message.
     * Empty or "새 메시지 3개" style lines carry nothing.
     */
    fun noticeText(text: CharSequence?, bigText: CharSequence?, lastMessage: CharSequence?): String? {
        val raw = (lastMessage?.toString()?.takeIf { it.isNotBlank() } ?: bigText?.toString()?.takeIf { it.isNotBlank() } ?: text?.toString())
            ?.replace(Regex("\\s+"), " ")?.trim()
        if (raw.isNullOrEmpty()) return null
        if (Regex("""^(새 )?메시지 \d+개$|^\d+ new messages?$""").matches(raw)) return null
        return raw
    }
}

/** The same notice posted again (updated, re-shown on unlock) is sent once. Keeps keys for ten minutes. */
class SeenNotices(private val window: Long = 10 * 60_000L, private val max: Int = 300) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized fun firstTime(key: String, now: Long): Boolean {
        seen.entries.removeAll { now - it.value > window }
        if (seen.containsKey(key)) return false
        seen[key] = now
        while (seen.size > max) seen.remove(seen.keys.first())
        return true
    }
}

/** One notice below 20%; again only after the phone charged back past 30%. */
object BatteryRule {
    const val LOW = 20
    const val RESET = 30

    /** Returns (send now?, still armed?) for a reading. */
    fun check(level: Int, charging: Boolean, armed: Boolean): Pair<Boolean, Boolean> = when {
        charging || level >= RESET -> false to true
        level < LOW && armed -> true to false
        else -> false to armed
    }
}

data class SignalFlush(
    val sent: Int = 0,
    val dropped: Int = 0,
    val remaining: Int = 0,
    val unauthorized: Boolean = false,
    val retryLater: Boolean = false,
    val sentKinds: Set<String> = emptySet(),
)

/**
 * The retry queue, the only place signals are kept: on disk inside the app until the Mac took them, then gone.
 * Old items (past what the Mac accepts) are dropped. Sends 50 at a time.
 */
class SignalQueue(private val file: File?, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    private val flushLock = Mutex()
    private var queue: MutableList<SignalItem> = file?.takeIf { it.exists() }?.let {
        runCatching { ProtocolJson.decodeFromString(ListSerializer(SignalItem.serializer()), it.readText()).toMutableList() }.getOrNull()
    } ?: mutableListOf()

    suspend fun add(items: List<SignalItem>) = lock.withLock {
        queue.addAll(items.filter { it.isSendable })
        prune()
        save()
    }

    suspend fun add(item: SignalItem) = add(listOf(item))

    suspend fun count(): Int = lock.withLock { queue.size }
    suspend fun snapshot(): List<SignalItem> = lock.withLock { queue.toList() }
    suspend fun clear() = lock.withLock { queue.clear(); save() }

    /**
     * `send` posts one body and returns the raw answer, or throws an [ApiError].
     * A batch the Mac refuses as a whole (400) is tried one by one, so one bad item never blocks the rest.
     */
    suspend fun flush(send: suspend (JsonObject) -> String): SignalFlush = flushLock.withLock {
        var sent = 0; var dropped = 0
        val kinds = mutableSetOf<String>()
        while (true) {
            val batch = lock.withLock { prune(); queue.take(Signals.BATCH) }
            if (batch.isEmpty()) break
            val outcome = trySend(batch, send)
            when (outcome) {
                is Outcome.Sent -> { remove(batch.map { it.id }); sent += batch.size; kinds += batch.map { it.kind } }
                is Outcome.Stop -> return SignalFlush(sent, dropped, count(), outcome.unauthorized, !outcome.unauthorized, kinds)
                is Outcome.Refused -> {
                    if (batch.size == 1) { remove(batch.map { it.id }); dropped += 1; continue }
                    for (item in batch) {
                        when (val one = trySend(listOf(item), send)) {
                            is Outcome.Sent -> { sent += 1; kinds += item.kind }
                            is Outcome.Refused -> dropped += 1
                            is Outcome.Stop -> return SignalFlush(sent, dropped, count(), one.unauthorized, !one.unauthorized, kinds)
                        }
                        remove(listOf(item.id))
                    }
                }
            }
        }
        SignalFlush(sent, dropped, count(), sentKinds = kinds)
    }

    private sealed interface Outcome {
        object Sent : Outcome
        object Refused : Outcome
        data class Stop(val unauthorized: Boolean) : Outcome
    }

    private suspend fun trySend(batch: List<SignalItem>, send: suspend (JsonObject) -> String): Outcome = try {
        val answer = send(Signals.body(batch, now()))
        // `{ok:false, problem}` is an empty text the Mac explains: nothing to retry.
        if (Signals.stored(answer) == null) Outcome.Refused else Outcome.Sent
    } catch (e: ApiError) {
        when (e) {
            ApiError.Unauthorized -> Outcome.Stop(unauthorized = true)
            is ApiError.BadRequest, is ApiError.Refused -> Outcome.Refused
            // An older Mac without ingest: keep the queue until it learns.
            else -> Outcome.Stop(unauthorized = false)
        }
    }

    private suspend fun remove(ids: List<String>) = lock.withLock {
        val gone = ids.toSet()
        queue.removeAll { it.id in gone }
        save()
    }

    private fun prune() {
        val cutoff = now() - Signals.MAX_AGE_MS
        queue.removeAll { it.at < cutoff || !it.isSendable }
        if (queue.size > Signals.MAX_QUEUED) queue = queue.takeLast(Signals.MAX_QUEUED).toMutableList()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(ProtocolJson.encodeToString(ListSerializer(SignalItem.serializer()), queue))
            tmp.renameTo(f)
        }
    }
}
