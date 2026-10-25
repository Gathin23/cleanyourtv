package com.faizannazir.cleanyourtv.tv

import com.faizannazir.cleanyourtv.tv.adb.AdbMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbMessageTest {

    @Test
    fun testChecksumCalculation() {
        val data = "CNXN".toByteArray(Charsets.UTF_8)
        val checksum = AdbMessage.generateChecksum(data)
        assertEquals('C'.code + 'N'.code + 'X'.code + 'N'.code, checksum)
    }

    @Test
    fun testHeaderSerializationLength() {
        val data = "host::".toByteArray(Charsets.UTF_8)
        val msg = AdbMessage(AdbMessage.A_CNXN, 0x01000000, 4096, data)
        val bytes = msg.toByteArray()
        assertEquals(24 + data.size, bytes.size)
    }
}
