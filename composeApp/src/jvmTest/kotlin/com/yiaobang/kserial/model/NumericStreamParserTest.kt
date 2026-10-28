package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumericStreamParserTest {

    @Test
    fun parsesOneSamplePerLine() {
        val parser = NumericStreamParser()
        val samples = parser.feed("1,2,3\n4,5,6\n".encodeToByteArray())
        assertEquals(2, samples.size)
        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0), samples[0])
        assertContentEquals(doubleArrayOf(4.0, 5.0, 6.0), samples[1])
    }

    @Test
    fun keepsPartialLineUntilNewlineArrives() {
        val parser = NumericStreamParser()
        assertTrue(parser.feed("1,2".encodeToByteArray()).isEmpty())
        val samples = parser.feed(",3\n".encodeToByteArray())
        assertEquals(1, samples.size)
        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0), samples.single())
    }

    @Test
    fun extractsNumbersFromLabelledOutput() {
        val parser = NumericStreamParser()
        val samples = parser.feed("TEMP=23.5,HUM=61,PRESS=-3.2\n".encodeToByteArray())
        assertContentEquals(doubleArrayOf(23.5, 61.0, -3.2), samples.single())
    }

    @Test
    fun ignoresEmptyAndTextOnlyLines() {
        val parser = NumericStreamParser()
        val samples = parser.feed("BOOT OK\n\n\n1 2\n".encodeToByteArray())
        assertEquals(1, samples.size)
        assertContentEquals(doubleArrayOf(1.0, 2.0), samples.single())
    }

    @Test
    fun capsChannelCount() {
        val parser = NumericStreamParser(maxChannels = 3)
        val samples = parser.feed("1 2 3 4 5\n".encodeToByteArray())
        assertEquals(3, samples.single().size)
    }

    @Test
    fun handlesScientificNotation() {
        val parser = NumericStreamParser()
        val samples = parser.feed("1.5e-3,2E4\n".encodeToByteArray())
        assertContentEquals(doubleArrayOf(0.0015, 20000.0), samples.single())
    }

    @Test
    fun resetDropsPendingLine() {
        val parser = NumericStreamParser()
        parser.feed("12".encodeToByteArray())
        parser.reset()
        assertTrue(parser.feed("34\n".encodeToByteArray()).single().contentEquals(doubleArrayOf(34.0)))
    }
}
