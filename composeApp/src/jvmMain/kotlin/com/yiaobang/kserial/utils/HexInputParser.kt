package com.yiaobang.kserial.utils

/** HEX 输入的解析结果；错误时 bytes 为 null，合法空输入则返回空数组。 */
data class HexInputResult(
    val bytes: ByteArray?,
    val normalizedText: String,
    val error: String?
) {
    val isValid: Boolean get() = error == null
    val byteCount: Int get() = bytes?.size ?: 0
}

/**
 * 面向人工输入的宽松 HEX 解析器：接受空格、逗号、分号、下划线、短横线和 0x 前缀，
 * 但剥离分隔符后必须是偶数个严格十六进制数字。
 */
object HexInputParser {
    fun parse(input: String): HexInputResult {
        val compact = input
            .replace("0x", "", ignoreCase = true)
            .filterNot { it.isWhitespace() || it == ',' || it == ';' || it == '_' || it == '-' }

        if (compact.isBlank()) return HexInputResult(byteArrayOf(), "", null)

        val invalid = compact.firstOrNull { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
        if (invalid != null) {
            return HexInputResult(null, "", "Invalid HEX character: '$invalid'")
        }

        if (compact.length % 2 != 0) {
            return HexInputResult(null, "", "HEX input has an odd number of digits")
        }

        val bytes = ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return HexInputResult(bytes, bytes.toHexDisplayString(), null)
    }
}
