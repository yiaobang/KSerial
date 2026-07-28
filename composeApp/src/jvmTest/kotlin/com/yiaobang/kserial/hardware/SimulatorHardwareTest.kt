package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 分别使用 COM3/COM4 与 COM5/COM6 验证文本分帧和 HEX 定长分帧自动回复。 */
class SimulatorHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun textSimulatorHandlesFragmentedAndConsecutiveCommands() {
        HardwareTestSupport.requireHardware(pairCount = 2)
        val (peerPort, simulatorPort) = HardwareTestSupport.pairs[1]
        val peer = HardwareTestSupport.open(peerPort, baudRate = 9600)
        val config = HardwareTestSupport.tempConfig("kserial-text-simulator", """
            simulator {
              endpoint { port = "$simulatorPort", baudRate = 9600 }
              encode = "ASCII"
              delimiter = "\r\n"
              rules = [
                { request = "AT\r\n", response = "OK\r\n" },
                { request = "AT+VERSION\r\n", response = "KSerial 1.0\r\n" }
              ]
            }
        """)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.loadSimulator(config.toFile())
            assertTrue(viewModel.isSimulating)
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val expected = "OK\r\nKSerial 1.0\r\n".encodeToByteArray()
            val capture = SerialCapture(expected.size)
            peer.addDataListener(capture.listener)
            val fragments = listOf("A", "T\r\nAT+VER", "SION\r\n").map(String::encodeToByteArray)
            fragments.forEach { fragment ->
                assertEquals(
                    fragment.size,
                    HardwareTestSupport.writeAndTrace(peer, peerPort, simulatorPort, fragment),
                    "The simulator request fragment was not fully sent"
                )
            }

            val received = capture.await()
            HardwareTestSupport.traceReceived(peerPort, received)
            assertContentEquals(expected, received)
            HardwareTestSupport.eventually {
                viewModel.rxCount == fragments.sumOf(ByteArray::size).toLong() &&
                    viewModel.txCount == expected.size.toLong()
            }
        } finally {
            viewModel.onCleared()
            peer.closePort()
            Files.deleteIfExists(config)
        }
    }

    @Test
    fun hexFixedLengthSimulatorHandlesBackToBackFrames() {
        HardwareTestSupport.requireHardware(pairCount = 3)
        val (peerPort, simulatorPort) = HardwareTestSupport.pairs[2]
        val peer = HardwareTestSupport.open(peerPort, parityIndex = 2)
        val config = HardwareTestSupport.tempConfig("kserial-hex-simulator", """
            simulator {
              endpoint {
                port = "$simulatorPort"
                baudRate = 115200
                dataBits = 8
                stopBits = 1
                parity = "even"
                flowControl = "none"
              }
              encode = "HEX"
              packSize = 4
              rules = [
                { request = "01 03 00 00", response = "01 03 02 00 2A" },
                { request = "01 06 00 01", response = "01 06 00 01" }
              ]
            }
        """)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.loadSimulator(config.toFile())
            assertTrue(viewModel.isSimulating)
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val requests = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x01, 0x06, 0x00, 0x01)
            val expected = byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x2A, 0x01, 0x06, 0x00, 0x01)
            val capture = SerialCapture(expected.size)
            peer.addDataListener(capture.listener)
            assertEquals(
                requests.size,
                HardwareTestSupport.writeAndTrace(peer, peerPort, simulatorPort, requests),
                "The HEX simulator requests were not fully sent"
            )

            val received = capture.await()
            HardwareTestSupport.traceReceived(peerPort, received)
            assertContentEquals(expected, received)
            HardwareTestSupport.eventually {
                viewModel.rxCount == requests.size.toLong() && viewModel.txCount == expected.size.toLong()
            }
        } finally {
            viewModel.onCleared()
            peer.closePort()
            Files.deleteIfExists(config)
        }
    }
}
