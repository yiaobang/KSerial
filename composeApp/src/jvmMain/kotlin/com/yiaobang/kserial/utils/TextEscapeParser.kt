package com.yiaobang.kserial.utils

import java.io.ByteArrayOutputStream

/**
 * 将文本发送框中的可见转义序列转换为最终写入串口的字节。
 *
 * 除常用的 `\n`、`\r` 等转义外，还支持八进制、`\xHH` 原始字节、Unicode
 * 码点和 `\cX` 控制字符。无法识别或不完整的转义会原样保留，避免用户输入
 * 因解析失败而被静默吞掉。
 */
object TextEscapeParser {
    /** 不需要继续读取数字的单字符转义，数值即对应的原始字节。 */
    private val simpleEscapes = mapOf(
        '\\' to 0x5C,
        '\'' to 0x27,
        '"' to 0x22,
        '?' to 0x3F,
        'a' to 0x07,
        'b' to 0x08,
        'e' to 0x1B,
        'f' to 0x0C,
        'n' to 0x0A,
        'r' to 0x0D,
        's' to 0x20,
        't' to 0x09,
        'v' to 0x0B
    )

    fun decodeToByteArray(input: String): ByteArray {
        val output = ByteArrayOutputStream(input.length)
        var index = 0
        var plainStart = 0

        fun flushPlain(endExclusive: Int) {
            if (plainStart < endExclusive) {
                output.write(input.substring(plainStart, endExclusive).encodeToByteArray())
            }
        }

        while (index < input.length) {
            if (input[index] != '\\') {
                index++
                continue
            }

            flushPlain(index)
            val parsed = parseEscape(input, index)
            if (parsed == null) {
                // Preserve unknown, incomplete, and invalid escapes exactly as entered.
                output.write('\\'.code)
                index++
            } else {
                output.write(parsed.bytes)
                index = parsed.nextIndex
            }
            plainStart = index
        }

        flushPlain(input.length)
        return output.toByteArray()
    }

    private fun parseEscape(input: String, slashIndex: Int): ParsedEscape? {
        if (slashIndex + 1 >= input.length) return null
        val marker = input[slashIndex + 1]

        simpleEscapes[marker]?.let { value ->
            return ParsedEscape(byteArrayOf(value.toByte()), slashIndex + 2)
        }

        if (marker in '0'..'7') {
            // 与 C 风格一致，八进制转义最多读取三位，并限制在单字节范围内。
            var end = slashIndex + 1
            while (end < input.length && end < slashIndex + 4 && input[end] in '0'..'7') end++
            val value = input.substring(slashIndex + 1, end).toInt(8)
            return if (value <= 0xFF) ParsedEscape(byteArrayOf(value.toByte()), end) else null
        }

        if (marker == 'x') {
            // \x 严格表示一个原始字节，因此必须恰好跟两个十六进制数字。
            val value = input.fixedRadixValue(slashIndex + 2, digitCount = 2, radix = 16) ?: return null
            return ParsedEscape(byteArrayOf(value.toByte()), slashIndex + 4)
        }

        if (marker == 'u') {
            val first = input.fixedRadixValue(slashIndex + 2, digitCount = 4, radix = 16) ?: return null
            val firstChar = first.toChar()
            if (firstChar.isHighSurrogate()) {
                // 补充平面字符必须由一对合法的 UTF-16 代理项组成，不能单独发送半个字符。
                val secondSlash = slashIndex + 6
                if (secondSlash + 6 > input.length || input[secondSlash] != '\\' || input[secondSlash + 1] != 'u') {
                    return null
                }
                val second = input.fixedRadixValue(secondSlash + 2, digitCount = 4, radix = 16) ?: return null
                val secondChar = second.toChar()
                if (!secondChar.isLowSurrogate()) return null
                return ParsedEscape(
                    codePointBytes(Character.toCodePoint(firstChar, secondChar)),
                    secondSlash + 6
                )
            }
            if (firstChar.isLowSurrogate()) return null
            return ParsedEscape(codePointBytes(first), slashIndex + 6)
        }

        if (marker == 'U') {
            // \U 接受完整 Unicode 码点，但排除代理项区间和超出 Unicode 上限的值。
            val codePoint = input.fixedRadixValue(slashIndex + 2, digitCount = 8, radix = 16) ?: return null
            if (!Character.isValidCodePoint(codePoint) || codePoint in 0xD800..0xDFFF) return null
            return ParsedEscape(codePointBytes(codePoint), slashIndex + 10)
        }

        if (marker == 'c' && slashIndex + 2 < input.length) {
            // \c@..\c_ 映射到 0x00..0x1F，\c? 映射到 DEL(0x7F)。
            val control = input[slashIndex + 2]
            val value = when {
                control == '?' -> 0x7F
                control.uppercaseChar() in '@'..'_' -> control.uppercaseChar().code and 0x1F
                else -> return null
            }
            return ParsedEscape(byteArrayOf(value.toByte()), slashIndex + 3)
        }

        return null
    }

    private fun String.fixedRadixValue(start: Int, digitCount: Int, radix: Int): Int? {
        val end = start + digitCount
        if (end > length) return null
        val token = substring(start, end)
        if (token.any { it.digitToIntOrNull(radix) == null }) return null
        val value = token.toLong(radix)
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun codePointBytes(codePoint: Int): ByteArray =
        String(Character.toChars(codePoint)).encodeToByteArray()

    private data class ParsedEscape(val bytes: ByteArray, val nextIndex: Int)
}
