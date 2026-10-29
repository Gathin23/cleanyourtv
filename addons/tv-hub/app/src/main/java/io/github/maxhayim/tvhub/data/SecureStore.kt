package io.github.maxhayim.tvhub.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.maxhayim.tvhub.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts source settings with an AES key kept in the Android Keystore, so the key never sits in
 * the app's files. Some budget TVs ship a broken Keystore; there the value is kept in the app's
 * private storage unencrypted (marked "p:"), backups being off, and the README says so.
 */
object SecureStore {
    private const val ALIAS = "tvhub_sources"
    @Volatile private var broken = false

    fun encrypt(plain: String): String {
        if (!broken) {
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key())
                val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
                return "k:" + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
            } catch (e: Exception) {
                broken = true
                Log.w("Keystore unavailable, storing source settings in private storage only", e)
            }
        }
        return "p:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = try {
        when {
            stored.startsWith("k:") -> {
                val all = Base64.decode(stored.substring(2), Base64.NO_WRAP)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
                String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
            }
            stored.startsWith("p:") -> String(Base64.decode(stored.substring(2), Base64.NO_WRAP), Charsets.UTF_8)
            else -> null
        }
    } catch (e: Exception) {
        Log.w("Could not read a stored source", e)
        null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }
}
