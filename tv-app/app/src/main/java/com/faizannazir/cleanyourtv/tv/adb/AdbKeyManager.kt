package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator

class AdbKeyManager(private val context: Context) {

    private val keyFile = File(context.filesDir, "adb_key.rsa")

    fun getOrCreateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
    }

    fun hasKeys(): Boolean {
        return keyFile.exists()
    }

    fun deleteKeys(): Boolean {
        return if (keyFile.exists()) keyFile.delete() else true
    }
}
