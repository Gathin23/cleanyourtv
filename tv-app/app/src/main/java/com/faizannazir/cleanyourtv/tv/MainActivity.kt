package com.faizannazir.cleanyourtv.tv

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.faizannazir.cleanyourtv.tv.adb.AdbDeviceScanner
import com.faizannazir.cleanyourtv.tv.engine.BackupRestoreEngine
import com.faizannazir.cleanyourtv.tv.engine.SafetyRulesEngine
import com.faizannazir.cleanyourtv.tv.model.AppCategory
import com.faizannazir.cleanyourtv.tv.model.AppInfo
import com.faizannazir.cleanyourtv.tv.model.DeviceInfo
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
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
        btnRestore = findViewById(R.id.btnRestore)
        btnExport = findViewById(R.id.btnExport)

        safetyRulesEngine = SafetyRulesEngine()
        backupRestoreEngine = BackupRestoreEngine(this)

        initDiagnostics()

        btnScan.setOnClickListener { runScan() }
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
            report.append("- Protected Packages: $protectedCount\n")
            report.append("- Removable / Cleanup Candidates: $optionalCount\n\n")

            report.append("Detected Cleanup Candidates:\n")
            scannedApps.filter { it.category == AppCategory.OPTIONAL }.forEach { app ->
                report.append(" • [${app.packageName}] ${app.label} - ${app.recommendationReason}\n")
            }

            runOnUiThread {
                tvStatus.text = report.toString()
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
        tvStatus.text = "Restore Baseline Loaded:\n" +
                "- Timestamp: ${baseline.timestamp}\n" +
                "- Packages to Restore: ${plan.size}\n" +
                if (plan.isEmpty()) "All packages are already in baseline state." else plan.joinToString("\n") { " • ${it.packageName}" }
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
