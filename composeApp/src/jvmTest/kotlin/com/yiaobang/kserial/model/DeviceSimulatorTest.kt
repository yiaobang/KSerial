package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun delayedReplyIsSentAfterConfiguredDelay() {
        val request = "PING\r\n".encodeToByteArray()
        val response = "OK\r\n".encodeToByteArray()
        val simulator = DelimiterBasedSimulator(
            replies = mapOf(request.toHexString() to response),
            delimiter = "\r\n".encodeToByteArray(),
            ruleDelays = mapOf(request.toHexString() to ReplyDelay.fixed(150L))
        )
        val replies = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        simulator.onReply = { data, _ -> replies += data }
        val scope = CoroutineScope(Dispatchers.IO)
        simulator.replyScope = scope

        try {
            simulator.checkData("PING\r\n".encodeToByteArray())

            // 延迟期内不应已回复
            assertEquals(0, replies.size)
            val deadline = System.currentTimeMillis() + 2000
            while (replies.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertEquals(1, replies.size)
            assertContentEquals(response, replies.single())
        } finally {
            simulator.close()
            scope.cancel()
        }
    }

    @Test
    fun configResponseDelayAndRuleOverrideAreApplied() {
        // 覆盖 HOCON 键本身：responseDelay 作为全局值，rules[].delay 单独覆盖它。
        val conf = java.io.File.createTempFile("delay", ".conf").apply {
            deleteOnExit()
            writeText(
                """
                simulator {
                  encode = "ASCII"
                  delimiter = "\r\n"
                  responseDelay = 300
                  rules = [
                    { request = "PING\r\n", response = "PONG\r\n" },
                    { request = "FAST\r\n", response = "OK\r\n", delay = 0 }
                  ]
                }
                """.trimIndent()
            )
        }
        val simulator = DeviceSimulator.createDeviceSimulator(conf)
        assertTrue(simulator != null, "config should load")
        val replies = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        simulator.onReply = { data, _ -> replies += data }
        val scope = CoroutineScope(Dispatchers.IO)
        simulator.replyScope = scope

        try {
            // 未单独指定 delay 的规则用全局 300ms：100ms 时不应已回复。
            val slowStart = System.currentTimeMillis()
            simulator.checkData("PING\r\n".encodeToByteArray())
            Thread.sleep(100)
            assertEquals(0, replies.size, "global responseDelay should hold the reply back")
            while (replies.isEmpty() && System.currentTimeMillis() - slowStart < 2000) Thread.sleep(10)
            val slowElapsed = System.currentTimeMillis() - slowStart
            assertEquals(1, replies.size)
            assertTrue(slowElapsed >= 250, "expected >=250ms, was $slowElapsed")

            // 规则级 delay = 0 覆盖全局值：应立即回复。
            replies.clear()
            val fastStart = System.currentTimeMillis()
            simulator.checkData("FAST\r\n".encodeToByteArray())
            while (replies.isEmpty() && System.currentTimeMillis() - fastStart < 2000) Thread.sleep(5)
            val fastElapsed = System.currentTimeMillis() - fastStart
            assertEquals(1, replies.size)
            assertTrue(fastElapsed < 200, "rule delay should override the global value, was $fastElapsed")
        } finally {
            simulator.close()
            scope.cancel()
        }
    }

    @Test
    fun randomDelayParsesAndSamplesWithinRange() {
        val spec = ReplyDelay.parse("100~300")
        assertEquals(true, spec != null)
        repeat(100) {
            assertTrue(spec!!.sample() in 100..300)
        }
        // 固定值与非法输入
        assertEquals(500L, ReplyDelay.parse("500")?.sample())
        assertEquals(null, ReplyDelay.parse("300~100"))
        assertEquals(null, ReplyDelay.parse("abc"))
        assertEquals(null, ReplyDelay.parse("-50"))
        assertEquals(null, ReplyDelay.parse("700000"))
    }

    @Test
    fun closeDiscardsPendingDelayedReplies() {
        val request = "SLOW\r\n".encodeToByteArray()
        val response = "LATE\r\n".encodeToByteArray()
        val simulator = DelimiterBasedSimulator(
            replies = mapOf(request.toHexString() to response),
            delimiter = "\r\n".encodeToByteArray(),
            ruleDelays = mapOf(request.toHexString() to ReplyDelay.fixed(10_000L))
        )
        val replies = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        simulator.onReply = { data, _ -> replies += data }
        val scope = CoroutineScope(Dispatchers.IO)
        simulator.replyScope = scope

        try {
            simulator.checkData("SLOW\r\n".encodeToByteArray())
            simulator.close()
            Thread.sleep(100)

            // close 取消挂起中的延迟应答，不应发出
            assertEquals(0, replies.size)
        } finally {
            scope.cancel()
        }
    }
}
