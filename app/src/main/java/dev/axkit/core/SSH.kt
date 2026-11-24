package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

// The 터미널: the phone opens an SSH session straight to a Mac over Tailscale. Nothing here goes through the AX server.

/** A Mac the terminal can open. Saved on this phone only. */
@Serializable
data class SSHHost(
    val id: String = newId(),
    val name: String,
    val user: String,
    val address: String,
    val port: Int = 22,
) {
    /** `user@address` (and `:port` when it is not 22). */
    val target: String get() = "$user@$address" + (if (port == 22) "" else ":$port")

    /** Key for the remembered host key; the same Mac on another port is a different entry, like OpenSSH. */
    val knownHostsKey: String get() = if (port == 22) address else "[$address]:$port"

    companion object {
        fun newId(): String = "h_" + UUID.randomUUID().toString().take(8).lowercase()

        /**
         * `name=user@address[:port]` entries separated by commas (the `AX_SSH_HOSTS` build setting).
         * Bad entries are skipped. The id comes from the target, so the same preset is recognised on the next launch.
         */
        fun presets(text: String?): List<SSHHost> {
            if (text == null || text.startsWith("$(")) return emptyList()
            return text.split(",").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split("=", limit = 2).map { it.trim() }
                if (parts.size != 2 || parts[0].isEmpty()) null else parse(parts[1], name = parts[0])
            }
        }

        /** `user@address[:port]` → a host (null when it does not look right). */
        fun parse(target: String, name: String): SSHHost? {
            val at = target.split("@", limit = 2)
            if (at.size != 2 || at[0].isEmpty() || at[0].contains(" ")) return null
            var address = at[1]
            var port = 22
            val colon = address.lastIndexOf(':')
            if (colon >= 0 && !address.contains("]") && address.count { it == ':' } == 1) {
                val p = address.substring(colon + 1).toIntOrNull() ?: return null
                if (p !in 1..65535) return null
                port = p
                address = address.substring(0, colon)
            }
            address = address.trim('[', ']')
            if (address.isEmpty() || address.contains(" ")) return null
            return SSHHost(id = "preset:${at[0]}@$address:$port", name = name, user = at[0], address = address, port = port)
        }

        /** What the add/fix sheet saves, or null while a field is missing or wrong (저장 stays off). */
        fun built(id: String?, name: String, user: String, address: String, port: String): SSHHost? {
            val n = name.trim()
            val u = user.trim()
            val a = address.trim()
            val p = port.toIntOrNull()
            if (n.isEmpty() || u.isEmpty() || u.contains(" ") || a.isEmpty() || a.contains(" ") || p == null || p !in 1..65535) return null
            return SSHHost(id = id ?: newId(), name = n, user = u, address = a, port = p)
        }
    }
}

/**
 * The phone's own SSH key (Ed25519). The private half never leaves the phone (sealed by the Android Keystore); the public
 * half is one OpenSSH line to paste into `~/.ssh/authorized_keys` on each Mac.
 */
object SSHKeyFormat {
    /** The 32-byte public key for a 32-byte Ed25519 private key (seed). */
    fun publicKey(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    /** `ssh-ed25519 AAAA… comment` */
    fun openSSHLine(publicKey: ByteArray, comment: String): String =
        "ssh-ed25519 " + Base64.getEncoder().encodeToString(blob(publicKey)) + (if (comment.isEmpty()) "" else " $comment")

    /** The SSH wire form: string "ssh-ed25519", string <32 key bytes>. */
    fun blob(publicKey: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun string(bytes: ByteArray) {
            val n = bytes.size
            out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
            out.write(bytes)
        }
        string("ssh-ed25519".toByteArray(Charsets.UTF_8))
        string(publicKey)
        return out.toByteArray()
    }

    /** `SHA256:…` as `ssh-keygen -lf` prints it, for any OpenSSH public key line. */
    fun fingerprint(openSSHLine: String): String? {
        val parts = openSSHLine.split(" ").filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val blob = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return null
        val digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
        return "SHA256:" + digest.trim('=')
    }
}

/** Small string store the terminal keeps its lists in (app-private preferences on the phone, a map in tests). */
interface SSHStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

private val sshJson = Json { ignoreUnknownKeys = true }

/** Host keys seen before (trust on first use). A different key later means the Mac changed — or someone is in between. */
class KnownHosts(private val store: SSHStore) {
    sealed interface Check {
        data object New : Check
        data object Same : Check
        data class Changed(val previous: String) : Check
    }

    private val storeKey = "ssh.knownHosts"
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val all: Map<String, String>
        get() = store.get(storeKey)?.let { runCatching { sshJson.decodeFromString(mapSerializer, it) }.getOrNull() } ?: emptyMap()

    private fun write(map: Map<String, String>) = store.set(storeKey, sshJson.encodeToString(mapSerializer, map))

    @Synchronized fun check(hostKeyLine: String, host: String): Check {
        val saved = all[host] ?: return Check.New
        return if (sameKey(saved, hostKeyLine)) Check.Same else Check.Changed(saved)
    }

    @Synchronized fun remember(hostKeyLine: String, host: String) = write(all + (host to hostKeyLine))

    @Synchronized fun forget(host: String) = write(all - host)

    @Synchronized fun saved(host: String): String? = all[host]

    companion object {
        /** Type and key bytes decide; a comment does not. */
        fun sameKey(a: String, b: String): Boolean {
            val x = a.split(" ").filter { it.isNotEmpty() }.take(2)
            val y = b.split(" ").filter { it.isNotEmpty() }.take(2)
            return x.size == 2 && x == y
        }
    }
}

/** The hosts list on this phone. Presets from the build are added once; a preset the owner deleted stays deleted. */
class SSHHostList(private val store: SSHStore) {
    private val listKey = "ssh.hosts"
    private val seededKey = "ssh.seededPresets"
    private val hostsSerializer = ListSerializer(SSHHost.serializer())
    private val idsSerializer = ListSerializer(String.serializer())

    val hosts: List<SSHHost>
        get() = store.get(listKey)?.let { runCatching { sshJson.decodeFromString(hostsSerializer, it) }.getOrNull() } ?: emptyList()

    fun save(hosts: List<SSHHost>) = store.set(listKey, sshJson.encodeToString(hostsSerializer, hosts))

    fun seed(presets: List<SSHHost>) {
        val seeded = (store.get(seededKey)?.let { runCatching { sshJson.decodeFromString(idsSerializer, it) }.getOrNull() } ?: emptyList()).toMutableSet()
        val list = hosts.toMutableList()
        for (preset in presets) {
            if (preset.id in seeded) continue
            if (list.none { it.id == preset.id || it.target == preset.target }) list.add(preset)
            seeded.add(preset.id)
        }
        save(list)
        store.set(seededKey, sshJson.encodeToString(idsSerializer, seeded.sorted()))
    }
}

/** Plain words for what went wrong; never the raw error. */
object SSHWords {
    const val keyComment = "axkit-android"

    fun firstTime(fingerprint: String) = "처음 연결한 맥이라 이 맥의 열쇠를 기억했어요 ($fingerprint). 나중에 바뀌면 알려 드릴게요."
    const val leftForBackground = "앱을 벗어나서 연결을 끊었어요."
    const val ended = "연결이 끝났어요."
    const val disconnected = "연결을 끊었어요."

    fun failure(error: Throwable, hostName: String): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val text = chain.joinToString(" ") { (it::class.java.simpleName + " " + (it.message ?: "")) }.lowercase()
        return when {
            chain.any { it::class.java.simpleName == "UserAuthException" } || text.contains("authentication") || text.contains("exhausted available authentication") ->
                "${hostName}이(가) 이 휴대폰 열쇠를 아직 몰라요. 터미널 첫 화면의 공개키를 맥의 ~/.ssh/authorized_keys에 넣어 주세요."
            chain.any { it is SocketTimeoutException } || text.contains("timeout") || text.contains("timed out") ->
                "${hostName}에 닿지 않아요. 휴대폰과 맥 모두 Tailscale이 켜져 있는지 봐 주세요."
            chain.any { it is ConnectException && it.message.orEmpty().lowercase().contains("refused") } || text.contains("refused") ->
                "${hostName}이(가) 연결을 받지 않아요. 맥의 설정 › 일반 › 공유에서 '원격 로그인'을 켜 주세요."
            chain.any { it is UnknownHostException || it is NoRouteToHostException } || text.contains("unreachable") || text.contains("no route") || text.contains("resolve") ->
                "${hostName}을(를) 찾지 못했어요. 주소와 Tailscale 연결을 확인해 주세요."
            else -> "${hostName}에 연결하지 못했어요. 잠시 뒤 다시 해 주세요."
        }
    }
}
