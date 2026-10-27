package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class AdbKeyManager(private val context: Context) {

    private val privateKeyFile = File(context.filesDir, "adb_priv.key")
    private val publicKeyFile = File(context.filesDir, "adb_pub.key")

    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            try {
                val privBytes = privateKeyFile.readBytes()
                val pubBytes = publicKeyFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(pubKey, privKey)
            } catch (_: Exception) {}
        }

        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        try {
            privateKeyFile.writeBytes(keyPair.private.encoded)
            publicKeyFile.writeBytes(keyPair.public.encoded)
        } catch (_: Exception) {}

        return keyPair
    }

    fun signToken(token: ByteArray, keyPair: KeyPair): ByteArray {
        return try {
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initSign(keyPair.private)
            sig.update(token)
            sig.sign()
        } catch (_: Exception) {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
            cipher.doFinal(token)
        }
    }

    fun getAdbPublicKeyPayload(keyPair: KeyPair): ByteArray {
        val b64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        return "$b64 cleanYourTv@android\u0000".toByteArray(Charsets.UTF_8)
    }
}
