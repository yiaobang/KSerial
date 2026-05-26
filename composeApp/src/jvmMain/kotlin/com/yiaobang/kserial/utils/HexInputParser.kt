package com.yiaobang.kserial.utils

data class HexInputResult(
    val bytes: ByteArray?,
    val normalizedText: String,
    val error: String?
) {
    val isValid: Boolean get() = error == null
    val byteCount: Int get() = bytes?.size ?: 0
}

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
