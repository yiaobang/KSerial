package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CircularArrayTest {
    @Test
    fun keepsNewestBytesWhenCapacityIsExceeded() {
        val buffer = CircularArray(4)

        buffer.add(byteArrayOf(1, 2, 3))
        buffer.add(byteArrayOf(4, 5))

        assertEquals(4, buffer.size())
        assertContentEquals(byteArrayOf(2, 3, 4, 5), buffer.getElements())
    }

    @Test
    fun clearResetsWrappedBuffer() {
        val buffer = CircularArray(3)
        buffer.add(byteArrayOf(1, 2, 3))
        buffer.add(4)

        buffer.clear()
        buffer.add(byteArrayOf(7, 8))

        assertContentEquals(byteArrayOf(7, 8), buffer.getElements())
    }
}
