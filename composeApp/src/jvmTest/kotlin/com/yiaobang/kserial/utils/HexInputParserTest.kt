package com.yiaobang.kserial.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HexInputParserTest {

    @Test
    fun parsesStandardSpaceSeparatedInput() {
        val result = HexInputParser.parse("AA BB CC")
        assertTrue(result.isValid)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), result.bytes)
        assertEquals("AA BB CC", result.normalizedText)
    }

    @Test
    fun parsesWith0xPrefix() {
        val result = HexInputParser.parse("0xAA 0xBB")
        assertTrue(result.isValid)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), result.bytes)
    }

    @Test
    fun parsesCommaSeparatedInput() {
        val result = HexInputParser.parse("AA,BB,CC")
        assertTrue(result.isValid)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), result.bytes)
    }

    @Test
    fun parsesMixedDelimiters() {
        val result = HexInputParser.parse("AA;BB_CC-DD")
        assertTrue(result.isValid)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()), result.bytes)
    }

    @Test
    fun returnsErrorOnOddDigits() {
        val result = HexInputParser.parse("ABC")
        assertFalse(result.isValid)
        assertEquals(null, result.bytes)
        val error = assertNotNull(result.error)
        assertTrue(error.contains("odd number of digits"))
    }

    @Test
    fun returnsErrorOnInvalidCharacter() {
        val result = HexInputParser.parse("GG")
        assertFalse(result.isValid)
        assertEquals(null, result.bytes)
        val error = assertNotNull(result.error)
        assertTrue(error.contains("Invalid HEX character"))
    }

    @Test
    fun parsesEmptyInputAsEmptyByteArray() {
        val result = HexInputParser.parse("")
        assertTrue(result.isValid)
        assertNotNull(result.bytes)
        assertContentEquals(byteArrayOf(), result.bytes)
        assertEquals(0, result.byteCount)
    }

    @Test
    fun rejectsTokenWithEmbedded0xWithoutDelimiter() {
        // "0xAA0xBB" is a single token; removePrefix only removes leading 0x, leaving "AA0xBB" where 'x' is invalid hex
        val result = HexInputParser.parse("0xAA0xBB")
        assertFalse(result.isValid)
        assertEquals(null, result.bytes)
        val error = assertNotNull(result.error)
        assertTrue(error.contains("'x'"))

        // Also test "10x2" which previously would be replaced to "12" silently
        val result2 = HexInputParser.parse("10x2")
        assertFalse(result2.isValid)
        assertNotNull(result2.error)
    }

    @Test
    fun parses0xPrefixedWithDelimiters() {
        val result = HexInputParser.parse("0xAA,0xBB")
        assertTrue(result.isValid)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), result.bytes)
    }
}
