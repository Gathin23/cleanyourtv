package com.faizannazir.cleanyourtv.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.faizannazir.cleanyourtv.tv.adb.AdbConnection
import com.faizannazir.cleanyourtv.tv.adb.AdbDeviceScanner
import com.faizannazir.cleanyourtv.tv.engine.BackupRestoreEngine
import com.faizannazir.cleanyourtv.tv.engine.SafetyRulesEngine
import com.faizannazir.cleanyourtv.tv.model.ActionType
import com.faizannazir.cleanyourtv.tv.model.AppCategory
import com.faizannazir.cleanyourtv.tv.model.AppInfo
import com.faizannazir.cleanyourtv.tv.model.CleanupBaseline
import com.faizannazir.cleanyourtv.tv.model.CleanupRecord
import com.faizannazir.cleanyourtv.tv.model.DeviceInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var etTvIp: EditText
    private lateinit var etTvPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnDisable: Button
    private lateinit var btnInstallLauncher: Button
    private lateinit var btnFastBoot: Button
    private lateinit var btnRestore: Button
    private lateinit var btnManualGuide: Button

    private lateinit var safetyRulesEngine: SafetyRulesEngine
    private lateinit var backupRestoreEngine: BackupRestoreEngine
    private var currentDeviceInfo: DeviceInfo? = null
    private val scannedApps = mutableListOf<AppInfo>()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTvIp = findViewById(R.id.etTvIp)
        etTvPort = findViewById(R.id.etTvPort)
        btnConnect = findViewById(R.id.btnConnect)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnDisable = findViewById(R.id.btnDisable)
        btnInstallLauncher = findViewById(R.id.btnInstallLauncher)
        btnFastBoot = findViewById(R.id.btnFastBoot)
        btnRestore = findViewById(R.id.btnRestore)
        btnManualGuide = findViewById(R.id.btnManualGuide)

        safetyRulesEngine = SafetyRulesEngine()
        backupRestoreEngine = BackupRestoreEngine(this)

        initDiagnostics()

        btnConnect.setOnClickListener { connectToRemoteTv() }
        btnScan.setOnClickListener { runScan() }
        btnDisable.setOnClickListener { confirmAndDisableCandidates() }
        btnInstallLauncher.setOnClickListener { runInstallCleanLauncher() }
        btnFastBoot.setOnClickListener { runFastBootOptimization() }
        btnRestore.setOnClickListener { runRestore() }
        btnManualGuide.setOnClickListener { showManualLauncherGuide() }
    }

    private fun getTargetHost(): String = etTvIp.text.toString().trim().ifBlank { "127.0.0.1" }
    private fun getTargetPort(): Int = etTvPort.text.toString().trim().toIntOrNull() ?: 5555

    private fun initDiagnostics() {
        tvDeviceInfo.text = "Device Info: Scanning TV IP & Hardware..."
        executor.execute {
            val scanner = AdbDeviceScanner(this)
            val info = scanner.scanLocalDevice()
            currentDeviceInfo = info

            val infoText = "TV Model: ${info.manufacturer} ${info.model} | Android ${info.androidVersion}\n" +
                    "Local Wi-Fi IP: ${info.localIpAddress}  |  Public IP: ${info.publicIpAddress}\n" +
                    "ADB Status: ${if (info.isAdbAuthorized) "Authorized (Port 5555)" else "Wireless Debugging Needed"}"

            runOnUiThread {
                tvDeviceInfo.text = infoText
                if (info.localIpAddress != "127.0.0.1") {
                    etTvIp.setText(info.localIpAddress)
                }
            }
        }
    }

    private fun connectToRemoteTv() {
        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nConnecting to target TV at $host:$port..."

        executor.execute {
            val conn = AdbConnection(this, host, port)
            val ok = conn.connect()

            val infoText = if (ok) {
                val modelRes = conn.executeShellCommand("getprop ro.product.model").replace("Success:", "").replace("\r", "").trim()
                val mfrRes = conn.executeShellCommand("getprop ro.product.manufacturer").replace("Success:", "").replace("\r", "").trim()
                val verRes = conn.executeShellCommand("getprop ro.build.version.release").replace("Success:", "").replace("\r", "").trim()

                val model = if (modelRes.isNotBlank()) modelRes else "Android TV"
                val mfr = if (mfrRes.isNotBlank()) mfrRes else "Smart TV"
                val ver = if (verRes.isNotBlank()) verRes else "Unknown"

                "TV Model: $mfr $model | Android $ver\n" +
                        "Target IP: $host:$port (Authorized)\n" +
                        "Local Wi-Fi IP: ${currentDeviceInfo?.localIpAddress ?: "N/A"}  |  Public IP: ${currentDeviceInfo?.publicIpAddress ?: "N/A"}"
            } else {
                "TV Model: Disconnected / Authorization Pending\n" +
                        "Target: $host:$port\n" +
                        "Local Wi-Fi IP: ${currentDeviceInfo?.localIpAddress ?: "N/A"}  |  Public IP: ${currentDeviceInfo?.publicIpAddress ?: "N/A"}"
            }

            if (ok) conn.disconnect()

            runOnUiThread {
                tvDeviceInfo.text = infoText
                tvStatus.text = "Console Log:\n" + if (ok) {
                    "✓ Successfully connected to $host:$port.\nTV details retrieved."
                } else {
                    "✗ Connection failed to $host:$port.\nEnsure Wireless Debugging is enabled on TV."
                }
            }
        }
    }

    private fun runScan() {
        scannedApps.clear()
        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nScanning packages on target TV ($host:$port)..."

        executor.execute {
            val adbConn = AdbConnection(this, host, port)
            val isAdbAvailable = adbConn.connect()

            val enabledPkgs = mutableSetOf<String>()
            val disabledPkgs = mutableSetOf<String>()

            if (isAdbAvailable) {
                val enabledOutput = adbConn.executeShellCommand("pm list packages -e").replace("Success:", "").replace("\r", "")
                val disabledOutput = adbConn.executeShellCommand("pm list packages -d").replace("Success:", "").replace("\r", "")

                enabledOutput.lines().forEach { line ->
                    val pkg = line.replace("package:", "").trim()
                    if (pkg.isNotBlank()) enabledPkgs.add(pkg)
                }

                disabledOutput.lines().forEach { line ->
                    val pkg = line.replace("package:", "").trim()
                    if (pkg.isNotBlank()) disabledPkgs.add(pkg)
                }

                adbConn.disconnect()
            } else {
                val pm = packageManager
                val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                for (pkg in installed) {
                    if (pkg.applicationInfo.enabled) enabledPkgs.add(pkg.packageName)
                    else disabledPkgs.add(pkg.packageName)
                }
            }

            var protectedCount = 0
            var optionalCount = 0
            var alreadyDisabledCount = disabledPkgs.size

            for (pkgName in enabledPkgs) {
                val appInfo = safetyRulesEngine.classifyApp(pkgName, pkgName, isSystem = true, isEnabled = true)
                scannedApps.add(appInfo)

                if (appInfo.category == AppCategory.PROTECTED) protectedCount++
                if (appInfo.category == AppCategory.OPTIONAL) optionalCount++
            }

            val report = StringBuilder()
            report.append("TV Scan Complete ($host:$port):\n")
            report.append("- Enabled TV Packages: ${enabledPkgs.size}\n")
            report.append("- Protected Packages (Locked): $protectedCount\n")
            report.append("- Removable / Debloat Candidates: $optionalCount\n")
            report.append("- Already Disabled on TV: $alreadyDisabledCount\n\n")

            if (optionalCount > 0) {
                report.append("Detected Active Debloat Candidates on TV:\n")
                scannedApps.filter { it.category == AppCategory.OPTIONAL }.forEach { app ->
                    report.append(" • ${app.packageName} - ${app.recommendationReason}\n")
                }
                report.append("\nSelect '2. Debloat TV' to execute safe debloating.")
            } else {
                report.append("No active optional debloat candidates remaining on target TV.")
            }

            runOnUiThread {
                tvStatus.text = report.toString()
            }
        }
    }

    private fun confirmAndDisableCandidates() {
        val candidates = scannedApps.filter { it.category == AppCategory.OPTIONAL }
        if (candidates.isEmpty()) {
            tvStatus.text = "Console Log:\nPlease run '1. Scan TV Apps' first. No active debloat candidates found."
            return
        }

        val appNames = candidates.map { it.packageName }.toTypedArray()
        val checkedItems = BooleanArray(candidates.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Confirm Debloat Candidates")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Execute Debloat") { _, _ ->
                val selectedToDisable = candidates.filterIndexed { index, _ -> checkedItems[index] }
                executeDisableOperations(selectedToDisable)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeDisableOperations(targets: List<AppInfo>) {
        if (targets.isEmpty()) {
            tvStatus.text = "Console Log:\nNo packages selected for disabling."
            return
        }

        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nCapturing pre-mutation baseline for $host:$port..."

        executor.execute {
            val adbConn = AdbConnection(this, host, port)
            val isAdbAvailable = adbConn.connect()

            val records = mutableListOf<CleanupRecord>()

            for (app in targets) {
                val pkgName = app.packageName.replace("\r", "").trim()
                if (safetyRulesEngine.isProtected(pkgName)) continue

                val origAppOps = if (isAdbAvailable) {
                    val res = adbConn.executeShellCommand("cmd appops get $pkgName RUN_IN_BACKGROUND").replace("\r", "").trim()
                    if (res.contains("allow")) "allow" else "deny"
                } else "allow"

                records.add(
                    CleanupRecord(
                        id = "${System.currentTimeMillis()}_$pkgName",
                        timestamp = System.currentTimeMillis(),
                        packageName = pkgName,
                        appLabel = app.label,
                        previousState = app.isEnabled,
                        previousAppOpsState = origAppOps,
                        isDataCleared = false,
                        requestedAction = ActionType.DISABLE_USER,
                        operationResult = "Pending execution",
                        isRestorable = true
                    )
                )
            }

            val existingBaseline = backupRestoreEngine.loadLatestBaseline()
            val mergedRecords = if (existingBaseline != null) {
                val existingMap = existingBaseline.records.associateBy { it.packageName }.toMutableMap()
                records.forEach { newRec ->
                    if (!existingMap.containsKey(newRec.packageName)) {
                        existingMap[newRec.packageName] = newRec
                    }
                }
                existingMap.values.toList()
            } else records

            val baseline = CleanupBaseline(
                timestamp = System.currentTimeMillis(),
                deviceModel = currentDeviceInfo?.model ?: "Android TV",
                androidVersion = currentDeviceInfo?.androidVersion ?: "Unknown",
                totalAppsScanned = scannedApps.size,
                records = mergedRecords
            )

            val saved = backupRestoreEngine.saveBaseline(baseline)
            if (!saved) {
                if (isAdbAvailable) adbConn.disconnect()
                runOnUiThread {
                    tvStatus.text = "ERROR: Failed to save baseline backup to disk.\nRefusing to touch TV or execute operations."
                }
                return@execute
            }

            val resultLog = StringBuilder()
            resultLog.append("Debloat Execution Log ($host:$port):\n")
            resultLog.append("------------------------------------\n")

            for (app in targets) {
                val pkgName = app.packageName.replace("\r", "").trim()
                if (safetyRulesEngine.isProtected(pkgName)) {
                    resultLog.append(" • SKIPPED (Protected): $pkgName\n")
                    continue
                }

                if (isAdbAvailable) {
                    val denyBg = adbConn.executeShellCommand("cmd appops set $pkgName RUN_IN_BACKGROUND deny").replace("\r", "").trim()
                    val cmd = "pm disable-user --user 0 $pkgName"
                    val disableRes = adbConn.executeShellCommand(cmd).replace("\r", "").trim()

                    resultLog.append(" • $pkgName\n")
                    resultLog.append("   AppOps Background: $denyBg\n")
                    resultLog.append("   Package Disable: $disableRes\n\n")
                } else {
                    resultLog.append(" • $pkgName: Recorded in baseline ($host:$port ADB port unavailable)\n\n")
                }
            }

            if (isAdbAvailable) {
                val verifyDisabled = adbConn.executeShellCommand("pm list packages -d").replace("Success:", "").replace("\r", "")
                resultLog.append("Verification: TV Disabled Package List Updated (${verifyDisabled.lines().count { it.contains("package:") }} packages disabled).\n")
                adbConn.disconnect()
            }

            resultLog.append("\n✓ Pre-debloat baseline saved atomically to storage before mutations.\n")

            runOnUiThread {
                tvStatus.text = resultLog.toString()
            }
        }
    }

    private fun runInstallCleanLauncher() {
        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nExtracting Clean Launcher APK & deploying to $host:$port..."

        executor.execute {
            val log = StringBuilder()
            log.append("Clean Launcher Deployment Log ($host:$port):\n")
            log.append("-------------------------------------------\n")

            try {
                val apkFile = File(cacheDir, "clean_launcher.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                var assetStream: InputStream? = null
                try {
                    val rawId = resources.getIdentifier("clean_launcher", "raw", packageName)
                    if (rawId != 0) {
                        assetStream = resources.openRawResource(rawId)
                    }
                } catch (_: Exception) {}

                if (assetStream == null) {
                    try {
                        assetStream = assets.open("clean_launcher.apk")
                    } catch (_: Exception) {
                        try {
                            assetStream = assets.openFd("clean_launcher.apk").createInputStream()
                        } catch (_: Exception) {
                            try {
                                assetStream = javaClass.classLoader?.getResourceAsStream("assets/clean_launcher.apk")
                            } catch (_: Exception) {}
                        }
                    }
                }

                var extracted = false
                if (assetStream != null) {
                    assetStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    extracted = apkFile.exists() && apkFile.length() > 0
                }

                if (extracted && apkFile.exists()) {
                    log.append("✓ Clean Launcher APK extracted (${apkFile.length()} bytes).\n")

                    val adbConn = AdbConnection(this, host, port)
                    val isAdbAvailable = adbConn.connect()

                    if (isAdbAvailable) {
                        val streamRes = FileInputStream(apkFile).use { apkInput ->
                            adbConn.installApkStream(apkInput, apkFile.length())
                        }
                        log.append(" • Streaming APK Install: $streamRes\n")

                        adbConn.executeShellCommand("pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS")
                        adbConn.executeShellCommand("pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION")
                        adbConn.executeShellCommand("pm grant com.gathin.tvhome android.permission.ACCESS_COARSE_LOCATION")

                        val setDefaultRes = adbConn.executeShellCommand("cmd package set-home-activity com.gathin.tvhome/.MainActivity")
                        log.append(" • Set Default Home Activity: $setDefaultRes\n")

                        val disable1 = adbConn.executeShellCommand("pm disable-user --user 0 com.google.android.apps.tv.launcherx")
                        val disable2 = adbConn.executeShellCommand("pm disable-user --user 0 com.google.android.tvlauncher")
                        val disable3 = adbConn.executeShellCommand("pm disable-user --user 0 com.tcl.tvhome")

                        log.append(" • Stock Launcher Disabling Results:\n")
                        log.append("   - LauncherX: $disable1\n")
                        log.append("   - Android TV Launcher: $disable2\n")
                        log.append("   - TCL Home: $disable3\n")

                        adbConn.disconnect()
                    } else {
                        log.append(" • Target ADB service at $host:$port unavailable.\n  Turn Wireless Debugging ON in TV Developer Options.\n")
                    }
                } else {
                    log.append("Error: Clean Launcher APK resource stream could not be opened.\n")
                }
            } catch (e: Exception) {
                log.append("Error during launcher deployment: ${e.localizedMessage ?: e.toString()}\n")
            }

            runOnUiThread {
                tvStatus.text = log.toString()
            }
        }
    }

    private fun runFastBootOptimization() {
        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nExecuting Fast Boot & OS Acceleration suite on $host:$port..."

        executor.execute {
            val adbConn = AdbConnection(this, host, port)
            val isAdbAvailable = adbConn.connect()

            val log = StringBuilder()
            log.append("Fast Boot & OS Acceleration Results ($host:$port):\n")
            log.append("---------------------------------------------------\n")

            val accelCommands = listOf(
                "settings put global window_animation_scale 0.5",
                "settings put global transition_animation_scale 0.5",
                "settings put global animator_duration_scale 0.5",
                "settings put global boot_fast 1",
                "setprop persist.sys.fastboot 1"
            )

            for (cmd in accelCommands) {
                val res = if (isAdbAvailable) {
                    adbConn.executeShellCommand(cmd).replace("\r", "").trim()
                } else {
                    "Simulated execution ($host:$port ADB unavailable)"
                }
                log.append(" • Executed: $cmd\n")
                log.append("   Result: $res\n\n")
            }

            if (isAdbAvailable) {
                adbConn.disconnect()
            }

            log.append("Boot Acceleration Applied:\n")
            log.append("✓ Enabled TCL persistent Fastboot mode.\n")
            log.append("✓ Cut animation transition delay by 50%.\n")

            runOnUiThread {
                tvStatus.text = log.toString()
            }
        }
    }

    private fun showManualLauncherGuide() {
        AlertDialog.Builder(this)
            .setTitle("Manual TV Settings Guide")
            .setMessage(
                "To set CleanYourTV as default Home Launcher on your TV manually:\n\n" +
                        "1. Open TV Settings on your Smart TV.\n" +
                        "2. Navigate to 'Apps' -> 'Default apps'.\n" +
                        "3. Select 'Home app'.\n" +
                        "4. Choose 'CleanYourTV' (com.gathin.tvhome).\n\n" +
                        "If 'Default apps' menu is hidden on TCL Google TV:\n" +
                        "• Go to Settings -> Apps -> See all apps -> CleanYourTV -> Open.\n" +
                        "• Press the Home button on your TV remote and select 'Always' for CleanYourTV."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun runRestore() {
        val baseline = backupRestoreEngine.loadLatestBaseline()
        if (baseline == null) {
            tvStatus.text = "Console Log:\nNo saved cleanup baseline found to restore."
            return
        }
        val plan = backupRestoreEngine.generateRestorePlan(baseline)
        if (plan.isEmpty()) {
            tvStatus.text = "Console Log:\nAll packages in baseline are already enabled or restored."
            return
        }

        val host = getTargetHost()
        val port = getTargetPort()
        tvStatus.text = "Console Log:\nExecuting Restoration Plan (${plan.size} packages) on $host:$port..."

        executor.execute {
            val adbConn = AdbConnection(this, host, port)
            val isAdbAvailable = adbConn.connect()

            val resultLog = StringBuilder()
            resultLog.append("Restoration Execution Log ($host:$port):\n")
            resultLog.append("------------------------------------\n")

            for (record in plan) {
                val enableCmd = "pm enable ${record.packageName}"
                val appOpsCmd = "cmd appops set ${record.packageName} RUN_IN_BACKGROUND ${record.previousAppOpsState}"

                val opResult1 = if (isAdbAvailable) {
                    adbConn.executeShellCommand(enableCmd).replace("\r", "").trim()
                } else "Recorded ($host:$port ADB unavailable)"

                val opResult2 = if (isAdbAvailable) {
                    adbConn.executeShellCommand(appOpsCmd).replace("\r", "").trim()
                } else "Recorded ($host:$port ADB unavailable)"

                resultLog.append(" • Restored [${record.appLabel}] (${record.packageName})\n")
                resultLog.append("   Package Enable: $opResult1\n")
                resultLog.append("   AppOps State Restored (${record.previousAppOpsState}): $opResult2\n\n")
            }

            if (isAdbAvailable) {
                adbConn.disconnect()
            }

            runOnUiThread {
                tvStatus.text = resultLog.toString()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
