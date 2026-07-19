package com.yiaobang.kserial.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals

class TextEscapeParserTest {
    @Test
    fun leavesEmptyAndPlainUtf8TextUnchanged() {
        assertContentEquals(byteArrayOf(), TextEscapeParser.decodeToByteArray(""))
        assertContentEquals(
            "ASCII 中文 😀\r\n\t".encodeToByteArray(),
            TextEscapeParser.decodeToByteArray("ASCII 中文 😀\r\n\t")
        )
    }

    @Test
    fun decodesSimpleControlCharactersBackslashAndQuotes() {
        val input = "\\a\\b\\e\\f\\n\\r\\s\\t\\v\\\\\\'\\\"\\?"

        assertContentEquals(
            byteArrayOf(0x07, 0x08, 0x1B, 0x0C, 0x0A, 0x0D, 0x20, 0x09, 0x0B, 0x5C, 0x27, 0x22, 0x3F),
            TextEscapeParser.decodeToByteArray(input)
        )
    }

    @Test
    fun decodesOctalAndHexAsRawBytes() {
        assertContentEquals(
            byteArrayOf(0x00, 0x1B, 0x7F, 0xFF.toByte()),
            TextEscapeParser.decodeToByteArray("\\0\\033\\177\\xFF")
        )
    }

    @Test
    fun decodesEveryPossibleHexByte() {
        val input = (0..0xFF).joinToString("") { value -> "\\x%02X".format(value) }
        val expected = ByteArray(256) { index -> index.toByte() }

        assertContentEquals(expected, TextEscapeParser.decodeToByteArray(input))
    }

    @Test
    fun decodesEveryPossibleThreeDigitOctalByte() {
        val input = (0..0xFF).joinToString("") { value -> "\\%03o".format(value) }
        val expected = ByteArray(256) { index -> index.toByte() }

        assertContentEquals(expected, TextEscapeParser.decodeToByteArray(input))
    }

    @Test
    fun numericEscapesConsumeOnlyTheirDefinedDigits() {
        assertContentEquals(
            byteArrayOf(0x01, 0x0A, 0x53, '4'.code.toByte(), 0x41, '4'.code.toByte(), '2'.code.toByte()),
            TextEscapeParser.decodeToByteArray("\\1\\12\\1234\\x4142")
        )
    }

    @Test
    fun decodesUnicodeBmpSupplementaryAndSurrogatePairEscapes() {
        val expected = "A中😀😀".encodeToByteArray()

        assertContentEquals(
            expected,
            TextEscapeParser.decodeToByteArray("\\u0041\\u4E2D\\U0001F600\\uD83D\\uDE00")
        )
    }

    @Test
    fun decodesUnicodeBoundaryCodePoints() {
        val expected = buildString {
            appendCodePoint(0x0000)
            appendCodePoint(0xFFFF)
            appendCodePoint(0x10000)
            appendCodePoint(0x10FFFF)
        }.encodeToByteArray()

        assertContentEquals(
            expected,
            TextEscapeParser.decodeToByteArray("\\u0000\\uFFFF\\U00010000\\U0010FFFF")
        )
    }

    @Test
    fun decodesControlNotation() {
        assertContentEquals(
            byteArrayOf(0x00, 0x03, 0x1B, 0x1F, 0x7F),
            TextEscapeParser.decodeToByteArray("\\c@\\cC\\c[\\c_\\c?")
        )
    }

    @Test
    fun escapedBackslashIsNotDecodedRecursively() {
        assertContentEquals("value\\n".encodeToByteArray(), TextEscapeParser.decodeToByteArray("value\\\\n"))
    }

    @Test
    fun preservesUnknownIncompleteAndInvalidEscapes() {
        val input = "\\q|\\xG1|\\x1|\\u12|\\uD800|\\uDC00|\\U00110000|\\UFFFFFFFF|\\400|tail\\"

        assertContentEquals(input.encodeToByteArray(), TextEscapeParser.decodeToByteArray(input))
    }

    @Test
    fun parsesValidEscapesAfterMalformedOnes() {
        val input = "\\q\\n\\xG1\\r\\u12\\t"
        val expected = "\\q\n\\xG1\r\\u12\t".encodeToByteArray()

        assertContentEquals(expected, TextEscapeParser.decodeToByteArray(input))
    }
}
