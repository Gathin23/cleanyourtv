package com.faizannazir.cleanyourtv.tv.model

import java.io.Serializable

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val category: AppCategory,
    val recommendationReason: String = "",
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.LOW,
    val safetyCategory: String = "General"
) : Serializable
