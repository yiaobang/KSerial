package com.yiaobang.kserial.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HexUtilsTest {
    @Test
    fun parsesCompactAndSpacedHex() {
        assertContentEquals(byteArrayOf(0x01, 0xAB.toByte()), "01 AB".hexToByteArray())
        assertContentEquals(byteArrayOf(0x01, 0xAB.toByte()), "01AB".hexToByteArray())
    }

    @Test
    fun rejectsInvalidHex() {
        assertNull("ABC".hexToByteArray())
        assertNull("GG".hexToByteArray())
    }

    @Test
    fun formatsCanonicalHexKey() {
        assertEquals("00FF10", byteArrayOf(0x00, 0xFF.toByte(), 0x10).toHexString())
    }
}
