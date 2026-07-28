package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.TextEscapeParser
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 使用 COM1/COM2 虚拟对验证终端 ViewModel 和底层传输的真实双向收发。 */
class TerminalHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun terminalDecodesEscapesAndSendsThemOverThePort() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (peerPort, terminalPort) = HardwareTestSupport.pairs[0]
        val peer = HardwareTestSupport.open(peerPort)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.config.selectedPort = terminalPort
            viewModel.config.baudRate = 115200
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val source = "AT\\r\\n\\x00\\x7F\\xFF\\u4E2D"
            val expected = TextEscapeParser.decodeToByteArray(source)
            val capture = SerialCapture(expected.size)
            peer.addDataListener(capture.listener)

            viewModel.sendData(source)

            val received = capture.await()
            HardwareTestSupport.traceReceived(peerPort, received)
            assertContentEquals(expected, received)
            HardwareTestSupport.eventually {
                viewModel.txCount == expected.size.toLong() && viewModel.sendHistory.snapshot().firstOrNull() == source
            }
        } finally {
            viewModel.onCleared()
            peer.closePort()
        }
    }

    @Test
    fun terminalAppendsConfiguredTextLineEnding() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (peerPort, terminalPort) = HardwareTestSupport.pairs[0]
        val peer = HardwareTestSupport.open(peerPort)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.config.selectedPort = terminalPort
            viewModel.prefs.sendLineEnding = SendLineEnding.CRLF
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val expected = "AT\r\n".encodeToByteArray()
            val capture = SerialCapture(expected.size)
            peer.addDataListener(capture.listener)
            assertTrue(viewModel.sendData("AT"))

            val received = capture.await()
            HardwareTestSupport.traceReceived(peerPort, received)
            assertContentEquals(expected, received)
        } finally {
            viewModel.onCleared()
            peer.closePort()
        }
    }

    @Test
    fun terminalReceivesPeerDataAndUpdatesItsRxState() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (peerPort, terminalPort) = HardwareTestSupport.pairs[0]
        val peer = HardwareTestSupport.open(peerPort)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.config.selectedPort = terminalPort
            viewModel.config.baudRate = 115200
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val payload = "peer-to-terminal\r\n".encodeToByteArray()
            assertEquals(
                payload.size,
                HardwareTestSupport.writeAndTrace(peer, peerPort, terminalPort, payload)
            )

            HardwareTestSupport.eventually {
                viewModel.rxCount == payload.size.toLong() &&
                    viewModel.logs.any { it.type == LogType.RX && it.content == payload.decodeToString() }
            }
        } finally {
            viewModel.onCleared()
            peer.closePort()
        }
    }

    @Test
    fun loopSendUsesTheSameEscapeDecoderForEveryTransmission() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (peerPort, terminalPort) = HardwareTestSupport.pairs[0]
        val peer = HardwareTestSupport.open(peerPort)
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            viewModel.config.selectedPort = terminalPort
            viewModel.togglePort()
            assertTrue(viewModel.isPortOpen)

            val source = "LOOP\\r\\n"
            val packet = TextEscapeParser.decodeToByteArray(source)
            val capture = SerialCapture(packet.size * 3)
            peer.addDataListener(capture.listener)
            viewModel.inputText = source
            viewModel.prefs.loopIntervalText = "30"
            viewModel.prefs.loopSendEnabled = true

            viewModel.startLoopSend()
            val received = capture.await()
            HardwareTestSupport.traceReceived(peerPort, received)
            viewModel.prefs.loopSendEnabled = false
            viewModel.stopLoopSend()

            assertTrue(received.size >= packet.size * 3)
            received.copyOfRange(0, packet.size * 3).toList().chunked(packet.size).forEach { chunk ->
                assertContentEquals(packet, chunk.toByteArray())
            }
        } finally {
            viewModel.onCleared()
            peer.closePort()
        }
    }

    @Test
    fun lowLevelTransportPreservesEveryByteInBothDirections() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (leftPort, rightPort) = HardwareTestSupport.pairs[0]
        val left = HardwareTestSupport.open(leftPort)
        val right = HardwareTestSupport.open(rightPort)
        val allBytes = ByteArray(256) { it.toByte() }
        try {
            val atRight = SerialCapture(allBytes.size)
            right.addDataListener(atRight.listener)
            assertEquals(allBytes.size, HardwareTestSupport.writeAndTrace(left, leftPort, rightPort, allBytes))
            val receivedAtRight = atRight.await()
            HardwareTestSupport.traceReceived(rightPort, receivedAtRight)
            assertContentEquals(allBytes, receivedAtRight)

            val reversed = allBytes.reversedArray()
            val atLeft = SerialCapture(reversed.size)
            left.addDataListener(atLeft.listener)
            assertEquals(reversed.size, HardwareTestSupport.writeAndTrace(right, rightPort, leftPort, reversed))
            val receivedAtLeft = atLeft.await()
            HardwareTestSupport.traceReceived(leftPort, receivedAtLeft)
            assertContentEquals(reversed, receivedAtLeft)
        } finally {
            left.closePort()
            right.closePort()
        }
    }

    @Test
    fun closedPortRejectsWritesAndCanReconnect() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val (leftPort, rightPort) = HardwareTestSupport.pairs[0]
        val left = HardwareTestSupport.open(leftPort)
        val right = HardwareTestSupport.open(rightPort)
        try {
            left.closePort()
            assertEquals(-1, left.write(byteArrayOf(1)))
            assertTrue(left.openPort(leftPort, 115200, 8, 0, 0, 0))

            val expected = "reconnected".encodeToByteArray()
            val capture = SerialCapture(expected.size)
            right.addDataListener(capture.listener)
            assertEquals(expected.size, HardwareTestSupport.writeAndTrace(left, leftPort, rightPort, expected))
            val received = capture.await()
            HardwareTestSupport.traceReceived(rightPort, received)
            assertContentEquals(expected, received)
        } finally {
            left.closePort()
            right.closePort()
        }
    }
}
