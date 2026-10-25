package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import java.net.InetSocketAddress
import java.net.Socket

class AdbConnection(
    private val context: Context,
    val host: String = "127.0.0.1",
    val port: Int = 5555
) {
    private var socket: Socket? = null
    private var isConnected = false
    private val keyManager = AdbKeyManager(context)

    fun connect(): Boolean {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), 3000)
            socket = sock
            isConnected = true
            true
        } catch (_: Exception) {
            isConnected = false
            false
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}
