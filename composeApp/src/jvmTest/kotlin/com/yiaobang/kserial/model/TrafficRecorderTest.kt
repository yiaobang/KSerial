package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficRecorderTest {
    @Test
    fun pairsRequestWithFollowingResponseChunks() {
        val recorder = TrafficRecorder()
        recorder.onTx("PING\r\n".encodeToByteArray())
        // 应答被串口拆成多个 chunk，应合并为一个应答
        recorder.onRx("PO".encodeToByteArray())
        recorder.onRx("NG\r\n".encodeToByteArray())

        val pairs = recorder.pairs()
        assertEquals(1, pairs.size)
        assertContentEquals("PING\r\n".encodeToByteArray(), pairs[0].request)
        assertContentEquals("PONG\r\n".encodeToByteArray(), pairs[0].response)
    }

    @Test
    fun interleavedRequestsPairSeparately() {
        val recorder = TrafficRecorder()
        recorder.onTx("A\r\n".encodeToByteArray())
        recorder.onRx("AA\r\n".encodeToByteArray())
        recorder.onTx("B\r\n".encodeToByteArray())
        recorder.onRx("BB\r\n".encodeToByteArray())

        val pairs = recorder.pairs()
        assertEquals(2, pairs.size)
        assertContentEquals("B\r\n".encodeToByteArray(), pairs[1].request)
        assertContentEquals("BB\r\n".encodeToByteArray(), pairs[1].response)
    }

    @Test
    fun dropsRequestsWithoutResponseAndEchoPairs() {
        val recorder = TrafficRecorder()
        recorder.onTx("NOANSWER\r\n".encodeToByteArray())
        recorder.onTx("ECHO\r\n".encodeToByteArray())
        recorder.onRx("ECHO\r\n".encodeToByteArray()) // 回环：应答等于请求，丢弃
        recorder.onTx("OK\r\n".encodeToByteArray())
        recorder.onRx("FINE\r\n".encodeToByteArray())

        val pairs = recorder.pairs()
        assertEquals(1, pairs.size)
        assertContentEquals("OK\r\n".encodeToByteArray(), pairs.single().request)
    }

    @Test
    fun duplicateRequestsKeepLatestPair() {
        val recorder = TrafficRecorder()
        recorder.onTx("AT\r\n".encodeToByteArray())
        recorder.onRx("OLD\r\n".encodeToByteArray())
        recorder.onTx("AT\r\n".encodeToByteArray())
        recorder.onRx("NEW\r\n".encodeToByteArray())

        val pairs = recorder.pairs()
        assertEquals(2, pairs.size)
        assertContentEquals("NEW\r\n".encodeToByteArray(), pairs.last().response)
    }

    @Test
    fun clearResetsEvents() {
        val recorder = TrafficRecorder()
        recorder.onTx("A\r\n".encodeToByteArray())
        recorder.onRx("B\r\n".encodeToByteArray())
        assertTrue(recorder.pairCount() > 0)

        recorder.clear()

        assertEquals(0, recorder.pairCount())
    }
}
