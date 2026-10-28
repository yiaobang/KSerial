package com.yiaobang.kserial.model

import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.SerialTransportEvent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSequenceTest {

    @Test
    fun parsesConnectionDefaultsAndSteps() {
        val sequence = load(
            """
            test {
              connection { port = "COM7", baudRate = 57600, parity = "even", stopBits = "2" }
              defaults { timeout = 700, interval = 30 }
              steps = [
                { name = "握手", send = "AT\r\n", expect = "OK" },
                { name = "慢步骤", send = "AT+SLOW\r\n", expect = "OK", timeout = 2500 },
                { name = "静默", match = "silence", timeout = 200 }
              ]
            }
            """.trimIndent()
        )

        assertEquals("COM7", sequence.connection.port)
        assertEquals(57600, sequence.connection.baudRate)
        assertEquals(2, sequence.connection.parityIndex)
        assertEquals(2, sequence.connection.stopBitsIndex)
        assertEquals(700, sequence.defaultTimeoutMs)
        assertEquals(3, sequence.steps.size)
        assertEquals(700, sequence.steps[0].timeoutMs)
        assertEquals(2500, sequence.steps[1].timeoutMs)
        assertEquals(TestMatchMode.SILENCE, sequence.steps[2].match)
        assertContentEquals("AT\r\n".encodeToByteArray(), sequence.steps[0].send)
    }

    @Test
    fun hexStepDecodesSendBytes() {
        val sequence = load(
            """
            test {
              steps = [ { encoding = "hex", send = "0D 0A", expect = "4F 4B" } ]
            }
            """.trimIndent()
        )
        assertContentEquals(byteArrayOf(0x0D, 0x0A), sequence.steps.single().send)
        assertEquals(TestEncoding.HEX, sequence.steps.single().encoding)
    }

    @Test
    fun invalidConfigIsRejectedWithMessage() {
        assertFailsWith<IllegalArgumentException> {
            load("""test { defaults { timeout = 100 } }""")
        }
        assertFailsWith<IllegalArgumentException> {
            load("""test { steps = [ { expect = "([", match = "regex" } ] }""")
        }
        assertFailsWith<IllegalArgumentException> {
            load("""test { steps = [ { name = "empty" } ] }""")
        }
    }

    @Test
    fun runnerPassesMatchingStepsAndFailsMismatch() = runTest {
        val transport = ScriptedTransport(
            mapOf(
                "AT\r\n" to "OK\r\n".encodeToByteArray(),
                "AT+STATUS?\r\n" to "BUSY\r\n".encodeToByteArray()
            )
        )
        val sequence = load(
            """
            test {
              defaults { timeout = 300, interval = 0 }
              steps = [
                { name = "握手", send = "AT\r\n", expect = "OK" },
                { name = "状态", send = "AT+STATUS?\r\n", expect = "READY" }
              ]
            }
            """.trimIndent()
        )

        val report = TestSequenceRunner(transport).run(sequence)

        assertEquals(2, report.results.size)
        assertTrue(report.results[0].passed)
        assertFalse(report.results[1].passed)
        assertEquals("BUSY", report.results[1].actual)
        assertEquals(1, report.passedCount)
    }

    @Test
    fun silencePassesWithoutDataAndFailsWhenDeviceSpeaks() = runTest {
        val quiet = ScriptedTransport(emptyMap())
        // 设备每收到一条指令就额外乱发一帧：静默窗口内出现数据即判失败。
        val chatty = ScriptedTransport(emptyMap(), unsolicited = "BOOT\r\n".encodeToByteArray())
        val silentSequence = load(
            """
            test {
              defaults { timeout = 100 }
              steps = [ { name = "静默", match = "silence" } ]
            }
            """.trimIndent()
        )
        val chattySequence = load(
            """
            test {
              defaults { timeout = 100 }
              steps = [ { name = "静默", send = "AT", match = "silence" } ]
            }
            """.trimIndent()
        )

        assertTrue(TestSequenceRunner(quiet).run(silentSequence).allPassed)
        assertFalse(TestSequenceRunner(chatty).run(chattySequence).allPassed)
    }

    @Test
    fun hexExpectationComparesNormalizedHex() = runTest {
        val transport = ScriptedTransport(mapOf("AT" to byteArrayOf(0x4F, 0x4B)))
        val sequence = load(
            """
            test {
              defaults { timeout = 200 }
              steps = [ { encoding = "hex", send = "41 54", expect = "4f 4b" } ]
            }
            """.trimIndent()
        )
        val report = TestSequenceRunner(transport).run(sequence)
        assertTrue(report.allPassed, "expected normalized hex comparison to pass, got ${report.results.firstOrNull()?.actual}")
    }

    @Test
    fun regexModeMatchesPattern() = runTest {
        val transport = ScriptedTransport(mapOf("X" to "TEMP=23.5C\r\n".encodeToByteArray()))
        val sequence = load(
            """
            test {
              defaults { timeout = 200 }
              steps = [ { send = "X", expect = "TEMP=[0-9.]+C", match = "regex" } ]
            }
            """.trimIndent()
        )
        assertTrue(TestSequenceRunner(transport).run(sequence).allPassed)
    }

    private fun load(text: String): TestSequence {
        val file = File.createTempFile("kserial-test-", ".conf").apply {
            writeText(text)
            deleteOnExit()
        }
        return TestSequence.load(file)
    }

    /** 收到指定字节就立刻回一段固定内容；可选地在每次写入后额外"乱发"一条。 */
    private class ScriptedTransport(
        private val replies: Map<String, ByteArray>,
        private val unsolicited: ByteArray? = null
    ) : SerialTransport {
        private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
        private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

        override val lastErrorMessage: String? = null
        override val isOpen: Boolean get() = true

        override fun addDataListener(listener: (ByteArray) -> Unit) {
            dataListeners += listener
        }

        override fun removeDataListener(listener: (ByteArray) -> Unit) {
            dataListeners -= listener
        }

        override fun clearDataListeners() = dataListeners.clear()

        override fun addTransportListener(listener: (SerialTransportEvent) -> Unit) {
            transportListeners += listener
        }

        override fun removeTransportListener(listener: (SerialTransportEvent) -> Unit) {
            transportListeners -= listener
        }

        override fun clearTransportListeners() = transportListeners.clear()

        override fun openPort(
            portName: String,
            baudRate: Int,
            dataBits: Int,
            stopBitsIndex: Int,
            parityIndex: Int,
            flowControlIndex: Int
        ): Boolean = true

        override fun closePort() = Unit

        override fun setDtr(enabled: Boolean): Boolean = true

        override fun setRts(enabled: Boolean): Boolean = true

        override fun setBreak(enabled: Boolean): Boolean = true

        override fun lineSignals(): SerialLineSignals = SerialLineSignals()

        override fun write(data: ByteArray): Int {
            val text = String(data)
            replies[text]?.let { response -> dataListeners.forEach { it(response) } }
            unsolicited?.let { extra -> dataListeners.forEach { it(extra) } }
            return data.size
        }
    }
}
