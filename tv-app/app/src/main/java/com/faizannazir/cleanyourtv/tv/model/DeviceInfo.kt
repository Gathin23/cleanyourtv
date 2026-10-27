package com.faizannazir.cleanyourtv.tv.model

import java.io.Serializable

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildId: String,
    val localIpAddress: String,
    val publicIpAddress: String,
    val isAdbAuthorized: Boolean,
    val adbDiagnosticMessage: String
) : Serializable
