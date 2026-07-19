package com.yiaobang.kserial.utils

/** 协议工具面板支持的常见校验算法。 */
enum class ChecksumAlgorithm {
    CRC16_MODBUS,
    XOR,
    SUM8,
    LRC
}

/** 对原始发送字节计算校验值，不涉及显示文本或字符编码。 */
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
        // Modbus 在线路上按低字节在前、高字节在后的顺序附加 CRC。
        return byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte())
    }
}
