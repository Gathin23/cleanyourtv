package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import android.os.Build
import com.faizannazir.cleanyourtv.tv.model.DeviceInfo
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

class AdbDeviceScanner(private val context: Context) {

    fun scanLocalDevice(): DeviceInfo {
        val model = Build.MODEL ?: "Unknown TV"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val version = Build.VERSION.RELEASE ?: "Unknown"
        val sdkInt = Build.VERSION.SDK_INT

        val localIp = detectLocalIpAddress()
        val publicIp = detectPublicIpAddress()

        val conn = AdbConnection(context)
        val adbOk = conn.connect()
        if (adbOk) conn.disconnect()

        val diagMsg = if (adbOk) {
            "Local ADB service active on port 5555."
        } else {
            "ADB port 5555 refused. Enable Wireless Debugging in TV Developer Options."
        }

        return DeviceInfo(
            model = model,
            manufacturer = manufacturer,
            androidVersion = version,
            sdkInt = sdkInt,
            buildId = Build.ID ?: "",
            localIpAddress = localIp,
            publicIpAddress = publicIp,
            isAdbAuthorized = adbOk,
            adbDiagnosticMessage = diagMsg
        )
    }

    private fun detectLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun detectPublicIpAddress(): String {
        return try {
            val url = URL("https://api.ipify.org")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText().trim()
            } else {
                "Unavailable"
            }
        } catch (_: Exception) {
            "Unavailable"
        }
    }
}
