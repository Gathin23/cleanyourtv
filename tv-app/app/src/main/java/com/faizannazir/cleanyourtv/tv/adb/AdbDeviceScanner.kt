package com.faizannazir.cleanyourtv.tv.adb

import android.content.Context
import android.os.Build
import com.faizannazir.cleanyourtv.tv.model.DeviceInfo

class AdbDeviceScanner(private val context: Context) {

    fun scanLocalDevice(): DeviceInfo {
        val model = Build.MODEL ?: "Unknown TV"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val version = Build.VERSION.RELEASE ?: "Unknown"
        val sdkInt = Build.VERSION.SDK_INT

        val conn = AdbConnection(context)
        val adbOk = conn.connect()
        if (adbOk) conn.disconnect()

        val diagMsg = if (adbOk) {
            "Local ADB service responded on port 5555."
        } else {
            "Local ADB port 5555 closed/refused. Enable Wireless Debugging or ADB over network in TV Developer Options."
        }

        return DeviceInfo(
            model = model,
            manufacturer = manufacturer,
            androidVersion = version,
            sdkInt = sdkInt,
            buildId = Build.ID ?: "",
            isAdbAuthorized = adbOk,
            adbDiagnosticMessage = diagMsg
        )
    }
}
