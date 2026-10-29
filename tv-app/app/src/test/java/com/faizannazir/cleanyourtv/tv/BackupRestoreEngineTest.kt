package com.faizannazir.cleanyourtv.tv

import com.faizannazir.cleanyourtv.tv.model.ActionType
import com.faizannazir.cleanyourtv.tv.model.CleanupBaseline
import com.faizannazir.cleanyourtv.tv.model.CleanupRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreEngineTest {

    @Test
    fun testRestorabilityContract() {
        val restorableRecord = CleanupRecord(
            id = "1",
            timestamp = 1000L,
            packageName = "com.test.app",
            appLabel = "Test App",
            previousState = true,
            previousAppOpsState = "allow",
            isDataCleared = false,
            requestedAction = ActionType.DISABLE_USER,
            operationResult = "Success",
            isRestorable = true
        )
        assertTrue(restorableRecord.isRestorable)

        val dataWipedRecord = CleanupRecord(
            id = "2",
            timestamp = 1000L,
            packageName = "com.test.app2",
            appLabel = "Test App 2",
            previousState = true,
            previousAppOpsState = "allow",
            isDataCleared = true,
            requestedAction = ActionType.DISABLE_USER,
            operationResult = "Success",
            isRestorable = false
        )
        assertFalse(dataWipedRecord.isRestorable)
    }

    @Test
    fun testRestorePlanGeneration() {
        val baseline = CleanupBaseline(
            timestamp = 2000L,
            deviceModel = "Test TV",
            androidVersion = "12",
            totalAppsScanned = 10,
            records = listOf(
                CleanupRecord(
                    id = "1",
                    timestamp = 2000L,
                    packageName = "com.test.app1",
                    appLabel = "App 1",
                    previousState = true,
                    previousAppOpsState = "allow",
                    isDataCleared = false,
                    requestedAction = ActionType.DISABLE_USER,
                    operationResult = "Disabled",
                    isRestorable = true
                ),
                CleanupRecord(
                    id = "2",
                    timestamp = 2000L,
                    packageName = "com.test.app2",
                    appLabel = "App 2",
                    previousState = true,
                    previousAppOpsState = "allow",
                    isDataCleared = true,
                    requestedAction = ActionType.DISABLE_USER,
                    operationResult = "Wiped and Disabled",
                    isRestorable = false
                )
            )
        )

        val plan = baseline.records.filter { it.isRestorable && it.requestedAction == ActionType.DISABLE_USER }
        assertEquals(1, plan.size)
        assertEquals("com.test.app1", plan[0].packageName)
    }
}
