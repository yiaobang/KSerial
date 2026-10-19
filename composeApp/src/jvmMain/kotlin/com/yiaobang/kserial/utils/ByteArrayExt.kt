package com.yiaobang.kserial.utils

/**
 * ByteArray 与 String 之间的编码转换扩展函数
 * 替代原有的 CodeFormat 工具类和 DataUtils 工具类，采用 Kotlin 惯用的扩展函数风格
 */

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

/** ByteArray → HEX 字符串 (大写，无分隔) */
fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        chars[i * 2] = HEX_CHARS[v ushr 4]
        chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(chars)
}

/** ByteArray → HEX 字符串 (大写，空格分隔，用于日志显示) */
fun ByteArray.toHexDisplayString(): String {
    if (isEmpty()) return ""
    val chars = CharArray(size * 3 - 1)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        val offset = i * 3
        chars[offset] = HEX_CHARS[v ushr 4]
        chars[offset + 1] = HEX_CHARS[v and 0x0F]
        if (i < size - 1) chars[offset + 2] = ' '
    }
    return String(chars)
}

/**
 * HEX 字符串 → ByteArray
 * 自动清除空格和换行符，支持 "AA BB CC" 和 "AABBCC" 两种格式
 * @return 转换后的字节数组，格式错误时返回 null
 */
fun String.hexToByteArray(): ByteArray? {
    val clean = replace(" ", "").replace("\n", "").replace("\r", "")
    if (clean.length % 2 != 0) return null
    return try {
        ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (_: Exception) {
        null
    }
}

/** ByteArray → UTF-8 字符串 */
fun ByteArray.toUtf8String(): String = String(this, Charsets.UTF_8)

/** String → UTF-8 ByteArray */
fun String.toUtf8ByteArray(): ByteArray = toByteArray(Charsets.UTF_8)

/** 检查字符串是否全部由合法 HEX 字符组成 (含空格) */
fun String.isHexString(): Boolean {
    val hexChars = "0123456789ABCDEFabcdef "
    return all { it in hexChars }
}
