package com.faizannazir.cleanyourtv.tv

import com.faizannazir.cleanyourtv.tv.engine.SafetyRulesEngine
import com.faizannazir.cleanyourtv.tv.model.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafetyRulesEngineTest {

    private lateinit var engine: SafetyRulesEngine

    @Before
    fun setUp() {
        engine = SafetyRulesEngine()
    }

    @Test
    fun testHardProtectedPackagesAreProtected() {
        assertTrue(engine.isProtected("com.google.android.katniss"))
        assertTrue(engine.isProtected("com.google.android.youtube.tv"))
        assertTrue(engine.isProtected("com.android.settings"))
        assertTrue(engine.isProtected("com.gathin.tvhome"))
    }

    @Test
    fun testBluetoothAndPowerRemoteDriversAreProtected() {
        assertTrue(engine.isProtected("com.android.bluetooth"))
        assertTrue(engine.isProtected("com.tcl.bluetooth"))
        assertTrue(engine.isProtected("com.tcl.power"))
        assertTrue(engine.isProtected("com.tcl.standby"))
    }

    @Test
    fun testUserProtectedPackagesAreProtected() {
        engine.setUserProtectedPackages(setOf("com.my.custom.app"))
        assertTrue(engine.isProtected("com.my.custom.app"))
    }

    @Test
    fun testProtectedPackageClassification() {
        val info = engine.classifyApp("com.android.settings", "Settings", true, true)
        assertEquals(AppCategory.PROTECTED, info.category)
    }

    @Test
    fun testKnownTclDebloatClassification() {
        val info = engine.classifyApp("com.tcl.partnercustomizer", "Partner Customizer", true, true)
        assertEquals(AppCategory.OPTIONAL, info.category)

        val suspensionInfo = engine.classifyApp("com.tcl.suspension", "TCL Suspension", true, true)
        assertEquals(AppCategory.OPTIONAL, suspensionInfo.category)
    }

    @Test
    fun testUserAppClassificationIsOptional() {
        val info = engine.classifyApp("com.example.randomapp", "Random App", false, true)
        assertEquals(AppCategory.OPTIONAL, info.category)
    }

    @Test
    fun testCommandValidationRejectsProtectedPackages() {
        assertFalse(engine.validateCommand("com.android.settings", "pm disable-user --user 0 com.android.settings"))
        assertFalse(engine.validateCommand("com.android.bluetooth", "pm disable-user --user 0 com.android.bluetooth"))
    }

    @Test
    fun testCommandValidationAllowsPermittedCommands() {
        assertTrue(engine.validateCommand("com.tcl.partnercustomizer", "pm disable-user --user 0 com.tcl.partnercustomizer"))
        assertTrue(engine.validateCommand("com.tcl.partnercustomizer", "pm enable com.tcl.partnercustomizer"))
    }
}
