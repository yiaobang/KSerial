package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.TestRunnerViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 测试序列在真实串口上执行：外部端口扮演"被测设备"，按指令内容应答。
 * 覆盖 CONTAINS、HEX、超时失败和 SILENCE 四种匹配在真实链路上的行为。
 */
class TestRunnerHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun sequenceRunsAgainstARealDeviceAndReportsStepResults() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val devicePort = HardwareTestSupport.pairs[0].first
        val runnerPort = HardwareTestSupport.pairs[0].second

        // 被测设备脚本：文本命令回文本，0x01 单字节回 0x4F 0x4B，未知命令保持沉默。
        val device = HardwareTestSupport.open(devicePort, baudRate = 9600)
        val buffer = StringBuilder()
        device.addDataListener { chunk ->
            synchronized(buffer) {
                buffer.append(String(chunk, Charsets.ISO_8859_1))
                val text = buffer.toString()
                when {
                    text.contains("AT+PING\r\n") -> {
                        buffer.setLength(0)
                        device.write("OK\r\n".encodeToByteArray())
                    }
                    text.startsWith("\u0001") -> {
                        buffer.setLength(0)
                        device.write(byteArrayOf(0x4F, 0x4B))
                    }
                    text.contains("AT+WRONG\r\n") -> {
                        buffer.setLength(0)
                        device.write("BUSY\r\n".encodeToByteArray())
                    }
                    text.contains("AT+CALM\r\n") -> {
                        // 静默步骤的对端：收到也不回复。
                        buffer.setLength(0)
                    }
                }
            }
        }

        val config = HardwareTestSupport.tempConfig("kserial-hw-sequence", """
            test {
              connection { port = "$runnerPort", baudRate = 9600 }
              defaults { timeout = 2000, interval = 20 }
              steps = [
                { name = "心跳", send = "AT+PING\r\n", expect = "OK" },
                { name = "HEX", encoding = "hex", send = "01", expect = "4F 4B" },
                { name = "应答错误", send = "AT+WRONG\r\n", expect = "NOPE" },
                { name = "静默", send = "AT+CALM\r\n", match = "silence", timeout = 400 }
              ]
            }
        """)

        val viewModel = TestRunnerViewModel()
        try {
            viewModel.loadConfig(config.toFile())
            assertTrue(viewModel.canRun, "Loaded sequence should be runnable")
            viewModel.runSequence()

            HardwareTestSupport.eventually(timeoutMillis = 20_000) { !viewModel.running && viewModel.lastReport != null }
            val report = viewModel.lastReport!!
            assertEquals(4, report.results.size)
            assertEquals(3, report.passedCount)
            assertFalse(report.allPassed)

            assertTrue(report.results[0].passed, "CONTAINS step must pass")
            assertTrue(report.results[1].passed, "HEX step must pass")
            assertFalse(report.results[2].passed, "Mismatched expectation must fail")
            assertEquals("BUSY", report.results[2].actual)
            assertTrue(report.results[3].passed, "Silence step must pass")
            assertFalse(report.results[2].timedOut, "Mismatch is a content failure, not a timeout")
        } finally {
            viewModel.onCleared()
            device.closePort()
            Files.deleteIfExists(config)
        }
    }
}
