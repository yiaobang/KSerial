package com.yiaobang.kserial.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ChecksumUtilsTest {
    private val standardInput = "123456789".encodeToByteArray()

    @Test
    fun calculatesKnownChecksumVectors() {
        assertContentEquals(byteArrayOf(0x37, 0x4B), ChecksumUtils.calculate(standardInput, ChecksumAlgorithm.CRC16_MODBUS))
        assertContentEquals(byteArrayOf(0x31), ChecksumUtils.calculate(standardInput, ChecksumAlgorithm.XOR))
        assertContentEquals(byteArrayOf(0xDD.toByte()), ChecksumUtils.calculate(standardInput, ChecksumAlgorithm.SUM8))
        assertContentEquals(byteArrayOf(0x23), ChecksumUtils.calculate(standardInput, ChecksumAlgorithm.LRC))
    }
}
