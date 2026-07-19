package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.toHexString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DeviceSimulatorTest {
    @Test
    fun fixedLengthSimulatorProcessesMultipleFramesFromOneChunk() {
        val request = byteArrayOf(1, 2)
        val response = byteArrayOf(9)
        val simulator = FixedLengthSimulator(mapOf(request.toHexString() to response), packSize = 2)
        val replies = mutableListOf<ByteArray>()
        simulator.onReply = { data, _ -> replies += data }

        simulator.checkData(byteArrayOf(1, 2, 1, 2))

        assertEquals(2, replies.size)
        replies.forEach { assertContentEquals(response, it) }
    }

    @Test
    fun delimiterSimulatorHandlesDelimiterSplitAcrossChunks() {
        val request = "PING\r\n".encodeToByteArray()
        val response = "OK\r\n".encodeToByteArray()
        val simulator = DelimiterBasedSimulator(
            replies = mapOf(request.toHexString() to response),
            delimiter = "\r\n".encodeToByteArray()
        )
        val replies = mutableListOf<ByteArray>()
        simulator.onReply = { data, _ -> replies += data }

        simulator.checkData("PING\r".encodeToByteArray())
        simulator.checkData("\n".encodeToByteArray())

        assertEquals(1, replies.size)
        assertContentEquals(response, replies.single())
    }
}
