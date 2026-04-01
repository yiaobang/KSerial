package com.yiaobang.kserial.utils

/**
 * ByteArray 与 String 之间的编码转换扩展函数
 * 替代原有的 CodeFormat 工具类和 DataUtils 工具类，采用 Kotlin 惯用的扩展函数风格
 */

/** ByteArray → HEX 字符串 (大写，无分隔) */
fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

/** ByteArray → HEX 字符串 (大写，空格分隔，用于日志显示) */
fun ByteArray.toHexDisplayString(): String = joinToString(" ") { "%02X".format(it) }

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
