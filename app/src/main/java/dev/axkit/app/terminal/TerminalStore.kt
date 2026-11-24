package dev.axkit.app.terminal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.axkit.app.BuildConfig
import dev.axkit.core.KnownHosts
import dev.axkit.core.SSHHost
import dev.axkit.core.SSHHostList
import dev.axkit.core.SSHKeyFormat
import dev.axkit.core.SSHStore
import dev.axkit.core.SSHWords
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The terminal's lists (hosts, remembered host keys) in app-private preferences. Nothing secret lives here. */
class PrefsSSHStore(context: Context) : SSHStore {
    private val prefs = context.getSharedPreferences("ssh", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

/**
 * The phone's SSH key (Ed25519), the Android twin of the iOS Keychain item (this device only, not in backups): the
 * 32-byte private key is sealed with an AES-256-GCM key that never leaves the Android Keystore, the same way
 * [dev.axkit.app.KeystoreCredentialStore] keeps the pairing. Backups are off for the app. Never logged.
 */
class SSHKeyStore(context: Context, private val alias: String = "axkit.ssh") {
    private val prefs = context.getSharedPreferences("ssh-key", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** The saved private key (32-byte seed), or null. */
    @Synchronized fun load(): ByteArray? {
        val sealed = prefs.getString("ed25519", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            cipher.doFinal(bytes, 12, bytes.size - 12)
        }.getOrNull()?.takeIf { it.size == 32 }
    }

    /** The saved key, or a new one made and saved now. */
    @Synchronized fun loadOrCreate(): ByteArray = load() ?: replace()

    /** Makes a new key. Every Mac that trusted the old one must be given the new public line. */
    @Synchronized fun replace(): ByteArray {
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(seed)
        check(prefs.edit().putString("ed25519", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
        return seed
    }
}

/** The hosts, the phone's key and the remembered host keys for the 터미널. */
class TerminalStore(context: Context) {
    private val prefs = PrefsSSHStore(context.applicationContext)
    private val list = SSHHostList(prefs)
    val known = KnownHosts(prefs)
    private val keys = SSHKeyStore(context.applicationContext)

    var hosts by mutableStateOf<List<SSHHost>>(emptyList())
        private set
    var publicKeyLine by mutableStateOf<String?>(null)
        private set
    var problem by mutableStateOf<String?>(null)

    init {
        list.seed(SSHHost.presets(BuildConfig.AX_SSH_HOSTS))
        hosts = list.hosts
        loadKey()
    }

    private fun loadKey() {
        publicKeyLine = runCatching { SSHKeyFormat.openSSHLine(SSHKeyFormat.publicKey(keys.loadOrCreate()), SSHWords.keyComment) }
            .getOrElse { problem = "휴대폰 열쇠를 만들지 못했어요."; null }
    }

    val fingerprint: String? get() = publicKeyLine?.let { SSHKeyFormat.fingerprint(it) }

    fun privateKey(): ByteArray = keys.loadOrCreate()

    /** A new key; the old public line stops working on every Mac. */
    fun replaceKey(): Boolean = runCatching {
        publicKeyLine = SSHKeyFormat.openSSHLine(SSHKeyFormat.publicKey(keys.replace()), SSHWords.keyComment)
    }.onFailure { problem = "새 열쇠를 만들지 못했어요." }.isSuccess

    fun save(host: SSHHost) {
        val now = hosts.toMutableList()
        val i = now.indexOfFirst { it.id == host.id }
        if (i >= 0) {
            // A new address or port is a different machine as far as host keys go.
            if (now[i].knownHostsKey != host.knownHostsKey) known.forget(now[i].knownHostsKey)
            now[i] = host
        } else {
            now.add(host)
        }
        hosts = now
        list.save(now)
    }

    fun delete(host: SSHHost) {
        hosts = hosts.filter { it.id != host.id }
        known.forget(host.knownHostsKey)
        list.save(hosts)
    }
}
