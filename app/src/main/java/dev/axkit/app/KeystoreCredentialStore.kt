package dev.axkit.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.axkit.core.CredentialStore
import dev.axkit.core.ProtocolJson
import dev.axkit.core.ServerCredentials
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device token and Access service token live only here (the Android twin of the iOS Keychain item): the whole pairing is
 * sealed with an AES-256-GCM key that never leaves the Android Keystore, then kept in app-private preferences.
 * Backups are off for the app (allowBackup=false), so the sealed blob never leaves the phone either.
 */
class KeystoreCredentialStore(context: Context, private val alias: String = "axkit.pairing") : CredentialStore {
    private val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)

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

    @Synchronized override fun load(): ServerCredentials? {
        val sealed = prefs.getString("sealed", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            ProtocolJson.decodeFromString(ServerCredentials.serializer(), String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
        }.getOrNull()
    }

    @Synchronized override fun save(credentials: ServerCredentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = ProtocolJson.encodeToString(ServerCredentials.serializer(), credentials).toByteArray(Charsets.UTF_8)
        val sealed = cipher.iv + cipher.doFinal(plain)
        prefs.edit().putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    @Synchronized override fun clear() { prefs.edit().clear().commit() }
}
