package com.faizannazir.cleanyourtv.tv.model

import java.io.Serializable

enum class ActionType {
    DISABLE_USER,
    ENABLE
}

data class CleanupItem(
    val packageName: String,
    val appLabel: String,
    val action: ActionType,
    val reason: String
) : Serializable

data class CleanupRecord(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val previousState: Boolean,
    val previousAppOpsState: String = "allow",
    val isDataCleared: Boolean = false,
    val requestedAction: ActionType,
    val operationResult: String,
    val isRestorable: Boolean = !isDataCleared
) : Serializable

data class CleanupBaseline(
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val totalAppsScanned: Int,
    val records: List<CleanupRecord>
) : Serializable
