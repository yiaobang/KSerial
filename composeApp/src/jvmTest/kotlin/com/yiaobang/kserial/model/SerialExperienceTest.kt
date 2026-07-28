package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerialExperienceTest {
    @Test
    fun timeWindowCombinesChunksWhileRawModePreservesThem() {
        val assembler = ReceiveFrameAssembler()
        val chunks = listOf("AB".encodeToByteArray(), "CD".encodeToByteArray())

        val combined = assembler.append(chunks, ReceiveDisplayMode.TIME_WINDOW, "\\r\\n", 8)
        assertEquals(1, combined.frames.size)
        assertContentEquals("ABCD".encodeToByteArray(), combined.frames.single())

        val raw = assembler.append(chunks, ReceiveDisplayMode.RAW_CHUNKS, "\\r\\n", 8)
        assertEquals(2, raw.frames.size)
        assertContentEquals(chunks[0], raw.frames[0])
        assertContentEquals(chunks[1], raw.frames[1])
    }

    @Test
    fun delimiterAndFixedLengthModesKeepRemaindersAcrossCallbacks() {
        val delimiterAssembler = ReceiveFrameAssembler()
        assertTrue(
            delimiterAssembler.append(
                listOf("A\r".encodeToByteArray()), ReceiveDisplayMode.DELIMITER, "\\r\\n", 8
            ).frames.isEmpty()
        )
        val delimited = delimiterAssembler.append(
            listOf("\nB\r\nC".encodeToByteArray()), ReceiveDisplayMode.DELIMITER, "\\r\\n", 8
        )
        assertEquals(listOf("A\r\n", "B\r\n"), delimited.frames.map(ByteArray::decodeToString))
        assertContentEquals("C".encodeToByteArray(), delimiterAssembler.reset())

        val fixedAssembler = ReceiveFrameAssembler()
        assertTrue(
            fixedAssembler.append(listOf(byteArrayOf(1, 2)), ReceiveDisplayMode.FIXED_LENGTH, "", 3).frames.isEmpty()
        )
        val fixed = fixedAssembler.append(listOf(byteArrayOf(3, 4, 5, 6)), ReceiveDisplayMode.FIXED_LENGTH, "", 3)
        assertEquals(2, fixed.frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3), fixed.frames[0])
        assertContentEquals(byteArrayOf(4, 5, 6), fixed.frames[1])
    }

    @Test
    fun modeChangesResetOldRemainderAndRemainderIsBounded() {
        val assembler = ReceiveFrameAssembler(maxRemainderBytes = 4)
        assembler.append(listOf("old".encodeToByteArray()), ReceiveDisplayMode.DELIMITER, "\\n", 8)
        val changed = assembler.append(listOf("new".encodeToByteArray()), ReceiveDisplayMode.FIXED_LENGTH, "", 3)
        assertEquals(listOf("new"), changed.frames.map(ByteArray::decodeToString))

        val bounded = assembler.append(listOf("123456".encodeToByteArray()), ReceiveDisplayMode.DELIMITER, "\\n", 3)
        assertEquals(2, bounded.droppedRemainderBytes)
        assertContentEquals("3456".encodeToByteArray(), assembler.reset())
    }

    @Test
    fun lineEndingBytesMatchTheirNames() {
        assertContentEquals(byteArrayOf(), SendLineEnding.NONE.bytes)
        assertContentEquals(byteArrayOf(0x0A), SendLineEnding.LF.bytes)
        assertContentEquals(byteArrayOf(0x0D), SendLineEnding.CR.bytes)
        assertContentEquals(byteArrayOf(0x0D, 0x0A), SendLineEnding.CRLF.bytes)
    }
}
