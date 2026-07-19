package com.yiaobang.kserial.utils

enum class ChecksumAlgorithm {
    CRC16_MODBUS,
    XOR,
    SUM8,
    LRC
}

object ChecksumUtils {
    fun calculate(data: ByteArray, algorithm: ChecksumAlgorithm): ByteArray = when (algorithm) {
        ChecksumAlgorithm.CRC16_MODBUS -> crc16Modbus(data)
        ChecksumAlgorithm.XOR -> byteArrayOf(data.fold(0) { result, byte -> result xor byte.toUByte().toInt() }.toByte())
        ChecksumAlgorithm.SUM8 -> byteArrayOf(data.sumOf { it.toUByte().toInt() }.toByte())
        ChecksumAlgorithm.LRC -> byteArrayOf((-data.sumOf { it.toUByte().toInt() }).toByte())
    }

    private fun crc16Modbus(data: ByteArray): ByteArray {
        var crc = 0xFFFF
        data.forEach { byte ->
            crc = crc xor byte.toUByte().toInt()
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte())
    }
}
