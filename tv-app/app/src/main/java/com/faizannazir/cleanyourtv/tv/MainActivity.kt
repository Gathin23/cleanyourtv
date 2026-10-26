package com.faizannazir.cleanyourtv.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
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
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnDisable: Button
    private lateinit var btnRestore: Button
    private lateinit var btnExport: Button

    private lateinit var safetyRulesEngine: SafetyRulesEngine
    private lateinit var backupRestoreEngine: BackupRestoreEngine
    private var currentDeviceInfo: DeviceInfo? = null
    private val scannedApps = mutableListOf<AppInfo>()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnDisable = findViewById(R.id.btnDisable)
        btnRestore = findViewById(R.id.btnRestore)
        btnExport = findViewById(R.id.btnExport)

        safetyRulesEngine = SafetyRulesEngine()
        backupRestoreEngine = BackupRestoreEngine(this)

        initDiagnostics()

        btnScan.setOnClickListener { runScan() }
        btnDisable.setOnClickListener { confirmAndDisableCandidates() }
        btnRestore.setOnClickListener { runRestore() }
        btnExport.setOnClickListener { exportReport() }
    }

    private fun initDiagnostics() {
        tvDeviceInfo.text = "Detecting TV Hardware & Local ADB..."
        executor.execute {
            val scanner = AdbDeviceScanner(this)
            val info = scanner.scanLocalDevice()
            currentDeviceInfo = info

            val infoText = "Device: ${info.manufacturer} ${info.model} | Android ${info.androidVersion} (SDK ${info.sdkInt})\n" +
                    "ADB Status: ${if (info.isAdbAuthorized) "Authorized (Port 5555)" else "Not Connected"}\n" +
                    "Note: ${info.adbDiagnosticMessage}"

            runOnUiThread {
                tvDeviceInfo.text = infoText
            }
        }
    }

    private fun runScan() {
        scannedApps.clear()
        tvStatus.text = "Scanning installed applications..."

        executor.execute {
            val pm = packageManager
            val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)

            var protectedCount = 0
            var optionalCount = 0

            for (pkg in installed) {
                val isSys = (pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = pkg.applicationInfo.loadLabel(pm).toString()
                val isEnabled = pkg.applicationInfo.enabled

                val appInfo = safetyRulesEngine.classifyApp(pkg.packageName, label, isSys, isEnabled)
                scannedApps.add(appInfo)

                if (appInfo.category == AppCategory.PROTECTED) protectedCount++
                if (appInfo.category == AppCategory.OPTIONAL) optionalCount++
            }

            val report = StringBuilder()
            report.append("Scan Complete:\n")
            report.append("- Total Installed Packages: ${scannedApps.size}\n")
            report.append("- Protected Packages (Locked): $protectedCount\n")
            report.append("- Removable / Cleanup Candidates: $optionalCount\n\n")

            if (optionalCount > 0) {
                report.append("Detected Safe Cleanup Candidates:\n")
                scannedApps.filter { it.category == AppCategory.OPTIONAL }.forEach { app ->
                    report.append(" • [${app.packageName}] ${app.label} - ${app.recommendationReason}\n")
                }
                report.append("\nSelect '2. Disable Safe Candidates' to proceed with debloating.")
            } else {
                report.append("No safe optional debloat candidates found in current scan.")
            }

            runOnUiThread {
                tvStatus.text = report.toString()
            }
        }
    }

    private fun confirmAndDisableCandidates() {
        val candidates = scannedApps.filter { it.category == AppCategory.OPTIONAL }
        if (candidates.isEmpty()) {
            tvStatus.text = "Please run '1. Scan Apps' first. No safe cleanup candidates selected."
            return
        }

        val appNames = candidates.map { "${it.label} (${it.packageName})" }.toTypedArray()
        val checkedItems = BooleanArray(candidates.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Confirm Packages to Disable")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Disable Selected") { _, _ ->
                val selectedToDisable = candidates.filterIndexed { index, _ -> checkedItems[index] }
                executeDisableOperations(selectedToDisable)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeDisableOperations(targets: List<AppInfo>) {
        if (targets.isEmpty()) {
            tvStatus.text = "No packages selected for disabling."
            return
        }

        tvStatus.text = "Executing cleanup baseline backup & package disabling..."

        executor.execute {
            val records = mutableListOf<CleanupRecord>()
            val adbConn = AdbConnection(this)
            val isAdbAvailable = adbConn.connect()

            val resultLog = StringBuilder()
            resultLog.append("Cleanup Execution Log:\n")
            resultLog.append("------------------------------------\n")

            for (app in targets) {
                if (safetyRulesEngine.isProtected(app.packageName)) {
                    resultLog.append(" • SKIPPED (Protected): ${app.packageName}\n")
                    continue
                }

                val cmd = "pm disable-user --user 0 ${app.packageName}"
                val isValid = safetyRulesEngine.validateCommand(app.packageName, cmd)
                if (!isValid) {
                    resultLog.append(" • BLOCKED (Security Policy Violation): ${app.packageName}\n")
                    continue
                }

                val opResult = if (isAdbAvailable) {
                    adbConn.executeShellCommand(cmd)
                } else {
                    "Simulated baseline recorded (ADB Wireless Debugging port 5555 unavailable)"
                }

                records.add(
                    CleanupRecord(
                        id = "${System.currentTimeMillis()}_${app.packageName}",
                        timestamp = System.currentTimeMillis(),
                        packageName = app.packageName,
                        appLabel = app.label,
                        previousState = app.isEnabled,
                        requestedAction = ActionType.DISABLE_USER,
                        operationResult = opResult,
                        isRestorable = true
                    )
                )

                resultLog.append(" • [${app.label}] (${app.packageName})\n")
                resultLog.append("   Command: $cmd\n")
                resultLog.append("   Result: $opResult\n\n")
            }

            if (isAdbAvailable) {
                adbConn.disconnect()
            }

            val baseline = CleanupBaseline(
                timestamp = System.currentTimeMillis(),
                deviceModel = currentDeviceInfo?.model ?: "Android TV",
                androidVersion = currentDeviceInfo?.androidVersion ?: "Unknown",
                totalAppsScanned = scannedApps.size,
                records = records
            )

            val saved = backupRestoreEngine.saveBaseline(baseline)
            if (saved) {
                resultLog.append("Baseline backup successfully recorded in private storage.\n")
            } else {
                resultLog.append("Warning: Could not write baseline backup file.\n")
            }

            runOnUiThread {
                tvStatus.text = resultLog.toString()
            }
        }
    }

    private fun runRestore() {
        val baseline = backupRestoreEngine.loadLatestBaseline()
        if (baseline == null) {
            tvStatus.text = "No saved cleanup baseline found to restore."
            return
        }
        val plan = backupRestoreEngine.generateRestorePlan(baseline)
        if (plan.isEmpty()) {
            tvStatus.text = "All packages in baseline are already enabled or restored."
            return
        }

        tvStatus.text = "Executing Restoration Plan (${plan.size} packages)..."

        executor.execute {
            val adbConn = AdbConnection(this)
            val isAdbAvailable = adbConn.connect()

            val resultLog = StringBuilder()
            resultLog.append("Restoration Execution Log:\n")
            resultLog.append("------------------------------------\n")

            for (record in plan) {
                val cmd = "pm enable ${record.packageName}"
                val opResult = if (isAdbAvailable) {
                    adbConn.executeShellCommand(cmd)
                } else {
                    "Restoration command recorded (ADB port 5555 unavailable)"
                }

                resultLog.append(" • Restored [${record.appLabel}] (${record.packageName})\n")
                resultLog.append("   Result: $opResult\n\n")
            }

            if (isAdbAvailable) {
                adbConn.disconnect()
            }

            runOnUiThread {
                tvStatus.text = resultLog.toString()
            }
        }
    }

    private fun exportReport() {
        val baseline = backupRestoreEngine.loadLatestBaseline()
        val json = backupRestoreEngine.exportReportJson(baseline)
        tvStatus.text = "Exported Local Diagnostic Baseline Report (JSON):\n\n$json"
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
