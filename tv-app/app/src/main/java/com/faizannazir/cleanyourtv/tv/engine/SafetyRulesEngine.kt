package com.faizannazir.cleanyourtv.tv.engine

import com.faizannazir.cleanyourtv.tv.model.AppCategory
import com.faizannazir.cleanyourtv.tv.model.AppInfo
import com.faizannazir.cleanyourtv.tv.model.ConfidenceLevel

class SafetyRulesEngine {

    companion object {
        val HARD_PROTECTED_PACKAGES = setOf(
            "com.google.android.katniss",
            "com.google.android.googlequicksearchbox",
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.settings",
            "com.android.systemui",
            "com.tcl.settings",
            "com.tcl.tv",
            "com.android.providers.telephony",
            "com.android.wifi.resources",
            "com.android.networkstack",
            "com.google.android.tv.setupwraith",
            "com.google.android.tungsten.setupwrangler",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.gathin.tvhome"
        )

        val KNOWN_DEBLOAT_PATTERNS = mapOf(
            "com.tcl.partnercustomizer" to Pair("TCL Partner Customizer bloatware", AppCategory.OPTIONAL),
            "com.tcl.tv.recommendations" to Pair("TCL recommendation telemetry", AppCategory.OPTIONAL),
            "com.tcl.shop" to Pair("TCL Store bloatware", AppCategory.OPTIONAL),
            "com.amazon.amazonvideo.livingroom" to Pair("Amazon Prime Video", AppCategory.REVIEW_REQUIRED),
            "com.netflix.ninja" to Pair("Netflix TV app", AppCategory.REVIEW_REQUIRED)
        )
    }

    private val userProtectedPackages = mutableSetOf<String>()

    fun setUserProtectedPackages(packages: Collection<String>) {
        userProtectedPackages.clear()
        userProtectedPackages.addAll(packages)
    }

    fun isProtected(packageName: String): Boolean {
        return HARD_PROTECTED_PACKAGES.contains(packageName) || userProtectedPackages.contains(packageName)
    }

    fun classifyApp(packageName: String, appLabel: String, isSystem: Boolean, isEnabled: Boolean): AppInfo {
        if (isProtected(packageName)) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = isSystem,
                isEnabled = isEnabled,
                category = AppCategory.PROTECTED,
                recommendationReason = "Essential core functionality or protected application",
                confidenceLevel = ConfidenceLevel.HIGH,
                safetyCategory = "Protected"
            )
        }

        val known = KNOWN_DEBLOAT_PATTERNS[packageName]
        if (known != null) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = isSystem,
                isEnabled = isEnabled,
                category = known.second,
                recommendationReason = known.first,
                confidenceLevel = ConfidenceLevel.HIGH,
                safetyCategory = if (known.second == AppCategory.OPTIONAL) "Safe Cleanup Candidate" else "Review Required"
            )
        }

        if (isSystem) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = true,
                isEnabled = isEnabled,
                category = AppCategory.ESSENTIAL,
                recommendationReason = "System component without explicit debloat classification",
                confidenceLevel = ConfidenceLevel.MEDIUM,
                safetyCategory = "System Component"
            )
        }

        return AppInfo(
            packageName = packageName,
            label = appLabel,
            isSystem = false,
            isEnabled = isEnabled,
            category = AppCategory.UNKNOWN,
            recommendationReason = "User application / unknown component - default no action",
            confidenceLevel = ConfidenceLevel.LOW,
            safetyCategory = "Unknown"
        )
    }

    fun validateCommand(packageName: String, actionCommand: String): Boolean {
        if (isProtected(packageName)) {
            return false
        }
        return actionCommand.startsWith("pm disable-user") ||
                actionCommand.startsWith("pm enable") ||
                actionCommand.startsWith("pm list packages") ||
                actionCommand.startsWith("getprop") ||
                actionCommand.startsWith("dumpsys")
    }
}
