package com.faizannazir.cleanyourtv.tv.engine

import android.content.Context
import com.faizannazir.cleanyourtv.tv.model.ActionType
import com.faizannazir.cleanyourtv.tv.model.CleanupBaseline
import com.faizannazir.cleanyourtv.tv.model.CleanupRecord
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class BackupRestoreEngine(private val context: Context) {

    private val backupDir = File(context.filesDir, "backups").apply {
        if (!exists()) mkdirs()
    }

    private val latestBackupFile = File(backupDir, "latest_baseline.dat")

    fun saveBaseline(baseline: CleanupBaseline): Boolean {
        return try {
            ObjectOutputStream(latestBackupFile.outputStream()).use { out ->
                out.writeObject(baseline)
            }
            val timestampFile = File(backupDir, "baseline_${baseline.timestamp}.dat")
            ObjectOutputStream(timestampFile.outputStream()).use { out ->
                out.writeObject(baseline)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadLatestBaseline(): CleanupBaseline? {
        if (!latestBackupFile.exists()) return null
        return try {
            ObjectInputStream(latestBackupFile.inputStream()).use { input ->
                input.readObject() as? CleanupBaseline
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateRestorePlan(baseline: CleanupBaseline): List<CleanupRecord> {
        return baseline.records.filter { record ->
            record.requestedAction == ActionType.DISABLE_USER && record.previousState
        }
    }

    fun exportReportJson(baseline: CleanupBaseline?): String {
        val sb = java.lang.StringBuilder()
        sb.append("{\n")
        sb.append("  \"appName\": \"CleanYourTV TV Edition\",\n")
        sb.append("  \"exportTimestamp\": ${System.currentTimeMillis()},\n")
        if (baseline != null) {
            sb.append("  \"baselineTimestamp\": ${baseline.timestamp},\n")
            sb.append("  \"deviceModel\": \"${escapeJson(baseline.deviceModel)}\",\n")
            sb.append("  \"androidVersion\": \"${escapeJson(baseline.androidVersion)}\",\n")
            sb.append("  \"totalAppsScanned\": ${baseline.totalAppsScanned},\n")
            sb.append("  \"modifiedRecordsCount\": ${baseline.records.size},\n")
            sb.append("  \"records\": [\n")
            baseline.records.forEachIndexed { idx, rec ->
                sb.append("    {\n")
                sb.append("      \"packageName\": \"${escapeJson(rec.packageName)}\",\n")
                sb.append("      \"appLabel\": \"${escapeJson(rec.appLabel)}\",\n")
                sb.append("      \"action\": \"${rec.requestedAction}\",\n")
                sb.append("      \"result\": \"${escapeJson(rec.operationResult)}\"\n")
                sb.append("    }${if (idx < baseline.records.size - 1) "," else ""}\n")
            }
            sb.append("  ]\n")
        } else {
            sb.append("  \"status\": \"No baseline backup available\"\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}
