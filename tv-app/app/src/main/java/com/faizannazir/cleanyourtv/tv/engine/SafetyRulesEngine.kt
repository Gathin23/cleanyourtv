package com.faizannazir.cleanyourtv.tv.engine

import com.faizannazir.cleanyourtv.tv.model.AppCategory
import com.faizannazir.cleanyourtv.tv.model.AppInfo
import com.faizannazir.cleanyourtv.tv.model.ConfidenceLevel

class SafetyRulesEngine {

    companion object {
        val HARD_PROTECTED_PACKAGES = setOf(
            // Core System UI & Settings
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
            "com.android.providers.telephony",
            "com.android.wifi.resources",
            "com.android.networkstack",
            "com.google.android.tv.setupwraith",
            "com.google.android.tungsten.setupwrangler",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.gathin.tvhome",

            // Remote Control, Bluetooth, Power & Standby Hardware Services (LOCKED)
            "com.android.bluetooth",
            "com.tcl.bluetooth",
            "com.tcl.tv.bluetooth",
            "com.tcl.remote",
            "com.tcl.tv.remote",
            "com.tcl.power",
            "com.tcl.tv.power",
            "com.tcl.standby",
            "com.tcl.tv.standby",
            "com.tcl.system",
            "com.tcl.tv.system",
            "com.tcl.cec",
            "com.tcl.tv.cec",
            "com.android.hardware.input",
            "com.mediatek.tv.bluetooth",
            "com.mediatek.tv.power"
        )

        val KNOWN_DEBLOAT_PATTERNS = mapOf(
            // Messaging, Camera & Phone Utilities (Unneeded on TV)
            "com.android.mms" to Pair("SMS/MMS Messaging (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.messaging" to Pair("Android Messaging app (unneeded on TV)", AppCategory.OPTIONAL),
            "com.samsung.android.messaging" to Pair("Samsung Messaging (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.camera" to Pair("Stock Camera app (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.camera2" to Pair("Camera2 app (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.printspooler" to Pair("Print Spooler service (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.fmradio" to Pair("FM Radio app (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.stk" to Pair("SIM Toolkit app (unneeded on TV)", AppCategory.OPTIONAL),
            "com.android.cellbroadcastreceiver" to Pair("Cell Broadcast Receiver (unneeded on TV)", AppCategory.OPTIONAL),

            // TCL Specific Bloatware & Telemetry
            "com.tcl.partnercustomizer" to Pair("TCL Partner Customizer bloatware", AppCategory.OPTIONAL),
            "com.tcl.tv.recommendations" to Pair("TCL recommendation telemetry", AppCategory.OPTIONAL),
            "com.tcl.shop" to Pair("TCL Store bloatware", AppCategory.OPTIONAL),
            "com.tcl.suspension" to Pair("TCL Suspension & promotional overlay", AppCategory.OPTIONAL),
            "com.tcl.guard" to Pair("TCL Guard background telemetry", AppCategory.OPTIONAL),
            "com.tcl.dashboard" to Pair("TCL Dashboard / promo panel", AppCategory.OPTIONAL),
            "com.tcl.tv" to Pair("TCL Live TV bloatware / default player", AppCategory.OPTIONAL),
            "com.tcl.voice" to Pair("TCL Voice Assistant bloatware", AppCategory.OPTIONAL),
            "com.tcl.useragreement" to Pair("TCL User Agreement prompt", AppCategory.OPTIONAL),
            "com.tcl.messagebox" to Pair("TCL Promotional Notifications", AppCategory.OPTIONAL),
            "com.tcl.videoplayer" to Pair("TCL Default Video Player bloatware", AppCategory.OPTIONAL),

            // Google TV / Android TV Non-Essential Promos & Recommendations
            "com.google.android.tv.recommendations" to Pair("Google TV recommendation channel", AppCategory.OPTIONAL),
            "com.google.android.tv.adservices" to Pair("Google TV Ad Services telemetry", AppCategory.OPTIONAL),
            "com.google.android.videos" to Pair("Google TV / Play Movies app", AppCategory.OPTIONAL),
            "com.google.android.music" to Pair("Google Play Music legacy app", AppCategory.OPTIONAL),

            // Pre-installed Third-Party Streaming / Promo Apps
            "com.amazon.amazonvideo.livingroom" to Pair("Amazon Prime Video preinstall", AppCategory.OPTIONAL),
            "com.netflix.ninja" to Pair("Netflix TV app preinstall", AppCategory.OPTIONAL),
            "com.spotify.tv.android" to Pair("Spotify TV preinstall", AppCategory.OPTIONAL),
            "com.disney.disneyplus" to Pair("Disney+ preinstall", AppCategory.OPTIONAL),
            "com.hbo.hbonow" to Pair("HBO Max preinstall", AppCategory.OPTIONAL),
            "com.zee5.player" to Pair("ZEE5 TV preinstall", AppCategory.OPTIONAL),
            "com.mubi.mubi" to Pair("MUBI TV preinstall", AppCategory.OPTIONAL),
            "com.plexapp.android" to Pair("Plex TV preinstall", AppCategory.OPTIONAL)
        )

        val BLOATWARE_KEYWORDS = listOf(
            "partner", "customizer", "recommendation", "telemetry", "shop", "promo",
            "advert", "analytics", "banner", "metrics", "tracker", "overlay",
            "camera", "messaging", "mms", "printspooler", "fmradio", "simtoolkit"
        )

        val CRITICAL_HARDWARE_KEYWORDS = listOf(
            "bluetooth", "power", "standby", "cec", "input", "remote", "key", "wifi"
        )
    }

    private val userProtectedPackages = mutableSetOf<String>()

    fun setUserProtectedPackages(packages: Collection<String>) {
        userProtectedPackages.clear()
        userProtectedPackages.addAll(packages)
    }

    fun isProtected(packageName: String): Boolean {
        val lowerPkg = packageName.lowercase()
        if (HARD_PROTECTED_PACKAGES.contains(packageName) || userProtectedPackages.contains(packageName)) {
            return true
        }
        return CRITICAL_HARDWARE_KEYWORDS.any { lowerPkg.contains(it) }
    }

    fun classifyApp(packageName: String, appLabel: String, isSystem: Boolean, isEnabled: Boolean): AppInfo {
        if (isProtected(packageName)) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = isSystem,
                isEnabled = isEnabled,
                category = AppCategory.PROTECTED,
                recommendationReason = "Essential TV hardware / remote / power component (locked)",
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
                safetyCategory = "Safe Debloat Candidate"
            )
        }

        val lowerPkg = packageName.lowercase()
        val lowerLabel = appLabel.lowercase()

        if (BLOATWARE_KEYWORDS.any { lowerPkg.contains(it) || lowerLabel.contains(it) }) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = isSystem,
                isEnabled = isEnabled,
                category = AppCategory.OPTIONAL,
                recommendationReason = "Detected promotional, camera, messaging, or partner bloatware pattern",
                confidenceLevel = ConfidenceLevel.MEDIUM,
                safetyCategory = "Safe Debloat Candidate"
            )
        }

        if (!isSystem) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = false,
                isEnabled = isEnabled,
                category = AppCategory.OPTIONAL,
                recommendationReason = "User-installed / non-essential app (removable/disableable)",
                confidenceLevel = ConfidenceLevel.MEDIUM,
                safetyCategory = "User Application"
            )
        }

        if (lowerPkg.startsWith("com.tcl.") || lowerPkg.startsWith("com.mediatek.tv.") || lowerPkg.startsWith("com.vendor.")) {
            return AppInfo(
                packageName = packageName,
                label = appLabel,
                isSystem = true,
                isEnabled = isEnabled,
                category = AppCategory.OPTIONAL,
                recommendationReason = "Non-essential OEM system bloatware candidate",
                confidenceLevel = ConfidenceLevel.MEDIUM,
                safetyCategory = "OEM System Bloat"
            )
        }

        return AppInfo(
            packageName = packageName,
            label = appLabel,
            isSystem = true,
            isEnabled = isEnabled,
            category = AppCategory.REVIEW_REQUIRED,
            recommendationReason = "Review required before disabling system package",
            confidenceLevel = ConfidenceLevel.LOW,
            safetyCategory = "Review Required"
        )
    }

    fun validateCommand(packageName: String, actionCommand: String): Boolean {
        if (isProtected(packageName)) {
            return false
        }
        return actionCommand.startsWith("pm disable-user") ||
                actionCommand.startsWith("pm enable") ||
                actionCommand.startsWith("pm clear") ||
                actionCommand.startsWith("pm trim-caches") ||
                actionCommand.startsWith("pm list packages") ||
                actionCommand.startsWith("cmd appops") ||
                actionCommand.startsWith("getprop") ||
                actionCommand.startsWith("dumpsys") ||
                actionCommand.startsWith("settings put global")
    }
}
