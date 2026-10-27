package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
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

    fun executeShellCommand(command: String): String {
        if (!isConnected && !connect()) {
            return "Error: Local ADB daemon not reachable on $host:$port. Enable Wireless Debugging on TV."
        }
        val sock = socket ?: return "Error: Socket null"
        return try {
            val outputStream: OutputStream = sock.getOutputStream()
            val inputStream: InputStream = sock.getInputStream()

            val openPayload = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            val openMsg = AdbMessage(AdbMessage.A_OPEN, 1, 0, openPayload)
            outputStream.write(openMsg.toByteArray())
            outputStream.flush()

            val headerBuffer = ByteArray(24)
            val readBytes = inputStream.read(headerBuffer)
            if (readBytes > 0) {
                "Command '$command' transmitted to local ADB daemon successfully."
            } else {
                "Command transmitted, no response from ADB daemon."
            }
        } catch (e: Exception) {
            "Execution error: ${e.message}"
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
