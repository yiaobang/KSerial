package com.yiaobang.kserial.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FrameParserTest {
    @Test
    fun splitsFixedFramesAndKeepsRemainder() {
        val result = FrameParser.fixedLength(byteArrayOf(1, 2, 3, 4, 5), 2)

        assertEquals(2, result.frames.size)
        assertContentEquals(byteArrayOf(1, 2), result.frames[0])
        assertContentEquals(byteArrayOf(3, 4), result.frames[1])
        assertContentEquals(byteArrayOf(5), result.remainder)
    }

    @Test
    fun delimiterFramesIncludeDelimiter() {
        val delimiter = byteArrayOf(0x0D, 0x0A)
        val result = FrameParser.delimiter(byteArrayOf(1, 0x0D, 0x0A, 2, 0x0D, 0x0A, 3), delimiter)

        assertEquals(2, result.frames.size)
        assertContentEquals(byteArrayOf(1, 0x0D, 0x0A), result.frames[0])
        assertContentEquals(byteArrayOf(2, 0x0D, 0x0A), result.frames[1])
        assertContentEquals(byteArrayOf(3), result.remainder)
    }
}
