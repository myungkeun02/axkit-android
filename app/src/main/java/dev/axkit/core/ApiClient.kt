package dev.axkit.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class ApiError(message: String) : Exception(message) {
    /** The Mac no longer knows this phone (401 JSON from the Mac itself). */
    object Unauthorized : ApiError("unauthorized")
    /** A card someone already answered (410 gone). */
    object Gone : ApiError("gone")
    object RateLimited : ApiError("rate")
    data class WrongCode(val attemptsLeft: Int?) : ApiError("wrong")
    object PairingExpired : ApiError("expired")
    object NotFound : ApiError("notfound")
    data class BadRequest(val error: String?) : ApiError("bad")
    /** The Mac said why in plain words. */
    data class Refused(val text: String) : ApiError("refused")
    data class Server(val status: Int, val error: String?) : ApiError("server $status")
    object Offline : ApiError("offline")
    object Decoding : ApiError("decoding")

    /** Very plain Korean for the screen (same words as iOS). */
    val friendlyMessage: String get() = when (this) {
        Unauthorized -> "맥과의 연결이 끊겼어요. 설정에서 다시 연결해 주세요."
        Gone -> "이미 처리된 카드예요."
        RateLimited -> "잠깐 너무 많이 보냈어요. 조금 뒤에 다시 해 주세요."
        is WrongCode -> attemptsLeft?.let { "번호가 맞지 않아요. ${it}번 더 해 볼 수 있어요." } ?: "번호가 맞지 않아요."
        PairingExpired -> "연결 QR이 만료됐어요. 맥에서 새 QR을 띄워 주세요."
        NotFound -> "맥에 아직 이 기능이 없어요."
        is BadRequest -> "보낸 내용을 맥이 이해하지 못했어요."
        is Refused -> text
        is Server -> "맥이 잠깐 대답을 못 해요. 조금 뒤에 다시 해 볼게요."
        Offline -> "맥에 닿지 않아요. 인터넷이나 Tailscale을 확인해 주세요."
        Decoding -> "맥이 보낸 내용을 읽지 못했어요."
    }

    /** Worth retrying later (outbox keeps the message). */
    val isTransient: Boolean get() = this is Offline || this is RateLimited || this is Server
}

val Throwable.friendly: String get() = (this as? ApiError)?.friendlyMessage
    ?: (this as? PairingLink.ParseException)?.friendlyMessage
    ?: "잠깐 문제가 생겼어요. 다시 해 볼게요."

/** How a card is answered (§4, §7.3). */
sealed interface CardAnswer {
    data class Decision(val value: String) : CardAnswer
    data class Option(val id: String) : CardAnswer
    data class Text(val text: String) : CardAnswer
    data class Select(val id: String, val values: List<String>) : CardAnswer
    data class Form(val id: String, val fields: Map<String, String>) : CardAnswer
}

@Serializable data class CardResult(val state: String = "closed", val reply: String? = null)
@Serializable data class SendResult(val seq: Int = 0, val duplicate: Boolean? = null)
@Serializable data class ChunkResult(val received: Int = 0, val complete: Boolean = false)

data class EventsPage(val events: List<AppEvent>, val lastSeq: Int, val oldestSeq: Int)

/** Talks to `{baseUrl}/m/v1/app/…` with the device token. Stateless apart from the credentials. */
class ApiClient(val credentials: ServerCredentials, val http: OkHttpClient = defaultHttp) {
    companion object {
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        private val jsonType = "application/json".toMediaType()

        @Serializable private data class PairResponse(
            val ok: Boolean = false, val deviceId: String? = null, val deviceName: String? = null, val baseUrl: String? = null,
            val token: String? = null, val error: String? = null, val attemptsLeft: Int? = null,
        )

        /** `POST {u}/m/p/<nonce>/app {code}` — no auth header. */
        suspend fun pair(link: PairingLink, deviceName: String = "안드로이드", http: OkHttpClient = defaultHttp): ServerCredentials {
            val builder = Request.Builder()
                .url("${link.baseUrl}/m/p/${link.nonce}/app")
                .post("""{"code":"${link.code}"}""".toRequestBody(jsonType))
            if (link.accessClientId != null && link.accessClientSecret != null) {
                builder.header("CF-Access-Client-Id", link.accessClientId).header("CF-Access-Client-Secret", link.accessClientSecret)
            }
            val (status, body, _) = load(http, builder.build())
            val parsed = runCatching { ProtocolJson.decodeFromString<PairResponse>(body) }.getOrNull()
            return when (status) {
                200 -> {
                    if (parsed == null || !parsed.ok || parsed.token == null || parsed.deviceId == null) throw ApiError.Decoding
                    // The Mac may name its own address; only take it if it passes the same rule.
                    val base = parsed.baseUrl?.takeIf { AddressPolicy.isAllowed(it) }?.let(AddressPolicy::normalized) ?: link.baseUrl
                    ServerCredentials(base, parsed.token, parsed.deviceId, parsed.deviceName ?: deviceName, link.accessClientId, link.accessClientSecret)
                }
                403 -> throw ApiError.WrongCode(parsed?.attemptsLeft)
                410 -> throw ApiError.PairingExpired
                429 -> throw ApiError.RateLimited
                else -> throw ApiError.Server(status, parsed?.error)
            }
        }

        /** Only the Mac's own JSON answer counts; a proxy's HTML 401 must never be taken as "unpaired". */
        fun isOwnJSON(contentType: String?) = (contentType ?: "").lowercase().contains("application/json")

        internal suspend fun load(http: OkHttpClient, request: Request): Triple<Int, String, String?> = withContext(Dispatchers.IO) {
            val response = try { http.newCall(request).await() } catch (e: IOException) { throw ApiError.Offline }
            response.use { Triple(it.code, it.body?.string() ?: "", it.header("Content-Type")) }
        }

        internal suspend fun loadBytes(http: OkHttpClient, request: Request): Triple<Int, ByteArray, String?> = withContext(Dispatchers.IO) {
            val response = try { http.newCall(request).await() } catch (e: IOException) { throw ApiError.Offline }
            response.use { Triple(it.code, it.body?.bytes() ?: ByteArray(0), it.header("Content-Type")) }
        }
    }

    val baseUrl: String get() = credentials.baseUrl

    fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val b = "$baseUrl/m/v1/app/$path".toHttpUrl().newBuilder()
        query.toSortedMap().forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    fun requestBuilder(path: String, query: Map<String, String> = emptyMap()): Request.Builder =
        Request.Builder().url(url(path, query)).also(::authorize)

    fun authorize(builder: Request.Builder) {
        builder.header("Authorization", "Bearer ${credentials.token}")
        if (credentials.accessClientId != null && credentials.accessClientSecret != null) {
            builder.header("CF-Access-Client-Id", credentials.accessClientId)
            builder.header("CF-Access-Client-Secret", credentials.accessClientSecret)
        }
    }

    /** Sends and checks the status; returns the raw body. */
    suspend fun send(request: Request): String {
        val (status, body, contentType) = load(http, request)
        check(status, body, contentType)
        return body
    }

    private fun check(status: Int, body: String, contentType: String?) {
        if (status in 200..299) return
        val obj = runCatching { ProtocolJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val error = obj?.get("error")?.jsonPrimitive?.contentOrNull
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull
        if (status == 400 && !message.isNullOrEmpty()) throw ApiError.Refused(message.take(200))
        throw when (status) {
            401 -> if (isOwnJSON(contentType)) ApiError.Unauthorized else ApiError.Server(status, error)
            404 -> ApiError.NotFound
            410 -> ApiError.Gone
            429 -> ApiError.RateLimited
            400, 413 -> ApiError.BadRequest(error)
            else -> ApiError.Server(status, error)
        }
    }

    suspend fun <T> get(serializer: KSerializer<T>, path: String, query: Map<String, String> = emptyMap()): T {
        val body = send(requestBuilder(path, query).get().build())
        return try { ProtocolJson.decodeFromString(serializer, body) } catch (e: Exception) { throw ApiError.Decoding }
    }

    suspend fun <T> post(serializer: KSerializer<T>, path: String, json: JsonElement): T {
        val body = send(requestBuilder(path).post(json.toString().toRequestBody(jsonType)).build())
        return try { ProtocolJson.decodeFromString(serializer, body) } catch (e: Exception) { throw ApiError.Decoding }
    }

    suspend fun postRaw(path: String, json: JsonElement): String = send(requestBuilder(path).post(json.toString().toRequestBody(jsonType)).build())

    /** 휴대폰 신호: `POST {base}/m/v1/ingest` with the same token (not under `/m/v1/app`). Returns the raw answer. */
    suspend fun ingest(json: JsonElement): String {
        val builder = Request.Builder().url("$baseUrl/m/v1/ingest").post(json.toString().toRequestBody(jsonType))
        authorize(builder)
        return send(builder.build())
    }

    // MARK: protocol calls

    suspend fun capabilities(): Capabilities = get(Capabilities.serializer(), "capabilities")
    suspend fun status(): ServerStatus = get(ServerStatus.serializer(), "status")

    suspend fun events(after: Int, limit: Int = 200, channel: String? = null): EventsPage {
        val query = buildMap { put("after", after.toString()); put("limit", limit.toString()); channel?.let { put("channel", it) } }
        val body = send(requestBuilder("events", query).get().build())
        val obj = runCatching { ProtocolJson.parseToJsonElement(body) as JsonObject }.getOrNull() ?: throw ApiError.Decoding
        val events = (obj["events"] as? JsonArray)?.let(EventDecoding::events) ?: emptyList()
        return EventsPage(events, obj["lastSeq"]?.jsonPrimitive?.intOrNull ?: 0, obj["oldestSeq"]?.jsonPrimitive?.intOrNull ?: 0)
    }

    suspend fun sendMessage(text: String? = null, choice: String? = null, channel: String? = null, clientId: String? = null, shared: Boolean = false): SendResult =
        post(SendResult.serializer(), "messages", buildJsonObject {
            text?.let { put("text", JsonPrimitive(it)) }
            choice?.let { put("choice", JsonPrimitive(it)) }
            if (shared) put("shared", JsonPrimitive(true))
            if (!channel.isNullOrEmpty()) put("channel", JsonPrimitive(channel))
            clientId?.let { put("clientId", JsonPrimitive(it)) }
        })

    /** `remember`: R11 — also make this a standing rule so the Mac stops asking about the same kind of thing. */
    suspend fun answerCard(cardId: String, answer: CardAnswer, remember: Boolean = false): CardResult =
        answerCard(cardId, cardBody(answer, remember))

    /** The same answer as a body kept in the outbox until the Mac is back. */
    suspend fun answerCard(cardId: String, body: JsonObject): CardResult {
        // Card ids are `[A-Za-z0-9_:.-]` (ax-hq: `org:d:<id>`, `p0:<id>`). `:` is fine in a path segment and the URL
        // builder escapes anything else once; escaping here too would double it (`%253A`, a 410 on ax-hq).
        val id = cardId.replace("/", "")
        return post(CardResult.serializer(), "cards/$id", body)
    }

    fun cardBody(answer: CardAnswer, remember: Boolean = false): JsonObject = run {
        val body = buildJsonObject {
            when (answer) {
                is CardAnswer.Decision -> put("decision", JsonPrimitive(answer.value))
                is CardAnswer.Option -> put("option", JsonPrimitive(answer.id))
                is CardAnswer.Text -> put("text", JsonPrimitive(answer.text))
                is CardAnswer.Select -> put("select", buildJsonObject {
                    put("id", JsonPrimitive(answer.id))
                    put("values", kotlinx.serialization.json.JsonArray(answer.values.map { JsonPrimitive(it) }))
                })
                is CardAnswer.Form -> put("form", buildJsonObject {
                    put("id", JsonPrimitive(answer.id))
                    put("fields", buildJsonObject { answer.fields.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
                })
            }
            if (remember) put("remember", JsonPrimitive(true))
        }
        body
    }

    /** `GET views/<name>` → `data`. */
    suspend fun <T> view(serializer: KSerializer<T>, name: String, query: Map<String, String> = emptyMap()): T {
        val body = send(requestBuilder("views/$name", query).get().build())
        return decodeData(serializer, body)
    }

    suspend fun <T> viewAction(serializer: KSerializer<T>, name: String, action: String, json: JsonElement): T =
        decodeData(serializer, postRaw("views/$name/$action", json))

    /** For actions whose answer the screen doesn't need. */
    suspend fun viewAction(name: String, action: String, json: JsonElement) { postRaw("views/$name/$action", json) }

    private fun <T> decodeData(serializer: KSerializer<T>, body: String): T = try {
        val obj = ProtocolJson.parseToJsonElement(body) as JsonObject
        ProtocolJson.decodeFromJsonElement(serializer, obj["data"] ?: JsonObject(emptyMap()))
    } catch (e: Exception) { throw ApiError.Decoding }

    suspend fun share(bytes: ByteArray, name: String, contentType: String): SendResult {
        val body = send(requestBuilder("share").header("X-File-Name", URLEncoder.encode(name, "UTF-8"))
            .post(bytes.toRequestBody(contentType.toMediaType())).build())
        return runCatching { ProtocolJson.decodeFromString<SendResult>(body) }.getOrElse { throw ApiError.Decoding }
    }

    /** §21.4 `POST read {channel, upTo}`: the owner read a room (other devices clear it too). */
    suspend fun markRead(channel: String, upTo: Int) { postRaw("read", buildJsonObject { put("channel", JsonPrimitive(channel)); put("upTo", JsonPrimitive(upTo)) }) }

    /** `POST messages/<seq>/react`: thumbs up / down on a bot's message ("more like this" / "less"). */
    suspend fun react(seq: Int, emoji: String) { postRaw("messages/$seq/react", buildJsonObject { put("emoji", JsonPrimitive(emoji)) }) }

    /** R7: the key goes straight to the Mac's Keychain and is never sent back. */
    suspend fun connectProvider(id: String, apiKey: String) { postRaw("providers/$id/key", buildJsonObject { put("apiKey", JsonPrimitive(apiKey)) }) }
    suspend fun disconnectProvider(id: String) { postRaw("providers/$id/disconnect", JsonObject(emptyMap())) }
    suspend fun unpair() { postRaw("unpair", JsonObject(emptyMap())) }

    suspend fun file(relativeUrl: String): ByteArray {
        val (status, bytes, contentType) = loadBytes(http, requestBuilder(relativeUrl).get().build())
        check(status, if (status in 200..299) "" else String(bytes), contentType)
        return bytes
    }

    /** `POST recordings/<id>/chunks/<index>`: raw audio bytes. Re-sending a chunk the Mac already has is harmless. */
    suspend fun uploadRecordingChunk(recording: PendingRecording, index: Int, bytes: ByteArray): ChunkResult {
        val request = requestBuilder("recordings/${recording.id}/chunks/$index")
            .header("X-Chunk-Total", recording.totalChunks.toString())
            .header("X-Started-At", java.time.Instant.ofEpochMilli(recording.startedAt).toString())
            .header("X-Duration-Sec", Math.round(recording.durationSec).toString())
            .post(bytes.toRequestBody("audio/mp4".toMediaType()))
            .build()
        val body = send(request)
        return runCatching { ProtocolJson.decodeFromString<ChunkResult>(body) }.getOrElse { throw ApiError.Decoding }
    }

    /**
     * §30 `POST dictation`: the recording's bytes as they are. The Mac takes a few seconds (whisper), so the read waits
     * longer than other calls. Every refusal (400 too_short · 413 too_long · 503 no_transcriber · 500 failed) carries a
     * `message` for the owner: it comes back as [ApiError.Refused].
     */
    suspend fun dictate(bytes: ByteArray, contentType: String): MacDictation.Answer {
        val request = requestBuilder("dictation").post(bytes.toRequestBody(contentType.toMediaType())).build()
        val (status, body, type) = load(dictationHttp, request)
        val obj = runCatching { ProtocolJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        if (status in 200..299) return MacDictation.decode(obj)
        val message = MacDictation.decode(obj).message
        if (message != null && status != 401) throw ApiError.Refused(message)
        check(status, body, type)
        throw ApiError.Server(status, null)
    }

    private val dictationHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build() }

    /**
     * §32 `GET app-updates/<name>` (this device's token) streamed into `into`, telling `progress` (0..1, or -1 when the
     * size is unknown). An APK is tens of MB, so it never goes through memory whole.
     */
    suspend fun download(path: String, into: java.io.File, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val response = try { downloadHttp.newCall(requestBuilder(path).get().build()).await() } catch (e: IOException) { throw ApiError.Offline }
        response.use { r ->
            if (!r.isSuccessful) { check(r.code, r.body?.string() ?: "", r.header("Content-Type")); throw ApiError.Server(r.code, null) }
            val body = r.body ?: throw ApiError.Decoding
            val total = body.contentLength()
            try {
                into.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024); var done = 0L; var told = -1
                        while (true) {
                            ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != told) { told = pct; progress(if (total > 0) done.toFloat() / total else -1f) }
                        }
                    }
                }
            } catch (e: IOException) { throw ApiError.Offline }
        }
    }

    private val downloadHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES).build() }

    fun streamRequest(lastSeq: Int): Request = requestBuilder("stream")
        .header("Accept", "text/event-stream")
        .header("Last-Event-ID", lastSeq.toString())
        .get().build()
}

inline fun <reified T> serializerOf(): KSerializer<T> = serializer()

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
