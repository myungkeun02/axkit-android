package dev.axkit.core

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder

/** `axkit://pair?u=<baseUrl>&n=<nonce>&c=<digits>[&ci=…&cs=…]` from the Mac's QR (APP-PROTOCOL §1–2). Same payload as iOS. */
data class PairingLink(
    val baseUrl: String,
    val nonce: String,
    val code: String,
    val accessClientId: String? = null,
    val accessClientSecret: String? = null,
) {
    enum class ParseError { NotALink, MissingPart, BadAddress, InsecureAddress }
    class ParseException(val reason: ParseError) : Exception(reason.name) {
        /** Very plain Korean for the screen. */
        val friendlyMessage: String get() = when (reason) {
            ParseError.NotALink -> "ax-kit 연결 QR이 아니에요."
            ParseError.MissingPart -> "연결 정보가 모자라요. 맥에서 새 QR을 띄워 주세요."
            ParseError.BadAddress -> "맥 주소를 읽지 못했어요."
            ParseError.InsecureAddress -> "안전하지 않은 주소예요. https나 집·Tailscale 주소만 쓸 수 있어요."
        }
    }

    companion object {
        fun parse(raw: String): PairingLink {
            val text = raw.trim()
            val uri = runCatching { URI(text) }.getOrNull() ?: throw ParseException(ParseError.NotALink)
            val isPair = uri.scheme?.lowercase() == "axkit" &&
                (uri.host?.lowercase() == "pair" || (uri.path ?: "").lowercase().endsWith("pair") || (uri.schemeSpecificPart ?: "").lowercase().startsWith("//pair"))
            if (!isPair) throw ParseException(ParseError.NotALink)
            val values = mutableMapOf<String, String>()
            (uri.rawQuery ?: "").split("&").filter { it.isNotEmpty() }.forEach { part ->
                val eq = part.indexOf('=')
                if (eq > 0) values[decode(part.substring(0, eq))] = decode(part.substring(eq + 1))
            }
            val address = values["u"]; val nonce = values["n"]; val code = values["c"]
            if (address == null || nonce.isNullOrEmpty() || code.isNullOrEmpty()) throw ParseException(ParseError.MissingPart)
            if (!Regex("^[A-Za-z0-9_-]{1,200}$").matches(nonce) || !Regex("^[0-9]{4,8}$").matches(code)) throw ParseException(ParseError.MissingPart)
            val url = runCatching { URI(address) }.getOrNull()
            if (url?.host == null) throw ParseException(ParseError.BadAddress)
            if (!AddressPolicy.isAllowed(address)) throw ParseException(ParseError.InsecureAddress)
            return PairingLink(
                AddressPolicy.normalized(address), nonce, code,
                values["ci"]?.takeIf { it.isNotEmpty() }, values["cs"]?.takeIf { it.isNotEmpty() },
            )
        }

        private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
    }
}

/** The app talks only to `https`, or to `http` on a private address (loopback, LAN, Tailscale 100.64/10). */
object AddressPolicy {
    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (scheme == "https") return true
        if (scheme != "http") return false
        return isPrivateHost(host)
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.trim('[', ']')
        if (h == "localhost" || h == "::1" || h.endsWith(".localhost")) return true
        val raw = h.split(".")
        val parts = raw.mapNotNull { it.toIntOrNull() }
        if (raw.size != 4 || parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return when {
            a == 127 || a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 100 && b in 64..127 -> true // Tailscale CGNAT range
            a == 169 && b == 254 -> true
            else -> false
        }
    }

    fun normalized(url: String): String = url.trimEnd('/')
}

/** What pairing hands back. Stored only sealed with the Android Keystore. */
@Serializable
data class ServerCredentials(
    val baseUrl: String,
    val token: String,
    val deviceId: String,
    val deviceName: String,
    val accessClientId: String? = null,
    val accessClientSecret: String? = null,
    val pairedAt: Long = System.currentTimeMillis(),
) {
    /** Host only, for showing which Mac without leaking anything secret. */
    val displayHost: String get() = runCatching { URI(baseUrl).host }.getOrNull() ?: baseUrl
}

interface CredentialStore {
    fun load(): ServerCredentials?
    fun save(credentials: ServerCredentials)
    fun clear()
}

class MemoryCredentialStore(private var value: ServerCredentials? = null) : CredentialStore {
    @Synchronized override fun load() = value
    @Synchronized override fun save(credentials: ServerCredentials) { value = credentials }
    @Synchronized override fun clear() { value = null }
}
