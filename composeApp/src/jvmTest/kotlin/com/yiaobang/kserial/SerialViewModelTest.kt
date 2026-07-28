package com.yiaobang.kserial

import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.ChecksumAlgorithm
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerialViewModelTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun inputBytesUseTextEscapesOrHexAccordingToMode() = withViewModel { viewModel ->
        viewModel.inputText = "A\\r\\n\\xFF"
        assertContentEquals(byteArrayOf(0x41, 0x0D, 0x0A, 0xFF.toByte()), viewModel.inputBytesOrNull())

        viewModel.prefs.hexSend = true
        viewModel.inputText = "41 0D 0A FF"
        assertContentEquals(byteArrayOf(0x41, 0x0D, 0x0A, 0xFF.toByte()), viewModel.inputBytesOrNull())

        viewModel.inputText = "invalid"
        assertEquals(null, viewModel.inputBytesOrNull())
    }

    @Test
    fun pendingSendByteCountIncludesTextLineEndingButNotHexSuffix() = withViewModel { viewModel ->
        viewModel.prefs.sendLineEnding = SendLineEnding.CRLF
        viewModel.prefs.hexSend = false
        assertEquals(4, viewModel.pendingSendByteCount("AT"))

        viewModel.prefs.hexSend = true
        assertEquals(2, viewModel.pendingSendByteCount("0A FF"))
        assertEquals(null, viewModel.pendingSendByteCount("0G"))
    }

    @Test
    fun checksumCanBeCalculatedAndAppendedToInput() = withViewModel { viewModel ->
        viewModel.inputText = "01 03 00 00"
        viewModel.prefs.hexSend = true
        val checksum = viewModel.checksum(ChecksumAlgorithm.CRC16_MODBUS)
        assertContentEquals(byteArrayOf(0xF1.toByte(), 0xD8.toByte()), checksum)

        assertTrue(viewModel.appendChecksum(ChecksumAlgorithm.CRC16_MODBUS))
        assertEquals("01 03 00 00 F1 D8", viewModel.inputText)
        assertTrue(viewModel.prefs.hexSend)
    }

    @Test
    fun historyNavigationAndQuickCommandPromotionPreserveEscapes() = withViewModel { viewModel ->
        viewModel.sendHistory.record("AT\r\n")
        viewModel.sendHistory.record("PING\n")
        viewModel.inputText = "draft"

        viewModel.previousHistory()
        assertEquals("PING\n", viewModel.inputText)
        viewModel.nextHistory()
        assertEquals("draft", viewModel.inputText)

        viewModel.promoteHistoryToQuickSend("STATUS\r\n\t")
        assertTrue(viewModel.prefs.quickCommandsText.contains("STATUS\\r\\n\\t"))
        val before = viewModel.prefs.quickCommandsText
        viewModel.promoteHistoryToQuickSend("STATUS\r\n\t")
        assertEquals(before, viewModel.prefs.quickCommandsText)
    }

    @Test
    fun invalidLoopIntervalStopsLoopAndAddsDiagnostic() = withViewModel { viewModel ->
        viewModel.prefs.loopSendEnabled = true
        viewModel.prefs.loopIntervalText = "0"

        viewModel.startLoopSend()

        assertFalse(viewModel.prefs.loopSendEnabled)
        assertTrue(viewModel.logs.any { it.content.contains("1") && it.content.contains("86400000") })
    }

    @Test
    fun simulatorConfigurationUpdatesEndpointAndReportsValidationErrors() = withViewModel { viewModel ->
        val valid = Files.createTempFile("kserial-vm-simulator", ".conf")
        val invalid = Files.createTempFile("kserial-vm-invalid", ".conf")
        try {
            Files.writeString(valid, """
                simulator {
                  endpoint { port = "COM88", baudRate = 115200, dataBits = 7, stopBits = 2, parity = "odd", flowControl = "xon/xoff" }
                  encode = "ASCII"
                  delimiter = "\n"
                  rules = [{ request = "PING\n", response = "PONG\n" }]
                }
            """.trimIndent())
            Files.writeString(invalid, "simulator { encode = HEX, packSize = 0, rules = [] }")

            viewModel.loadSimulator(valid.toFile())
            assertTrue(viewModel.isSimulating)
            assertFalse(viewModel.isPortOpen)
            assertEquals("COM88", viewModel.config.selectedPort)
            assertEquals(115200, viewModel.config.baudRate)
            assertEquals(7, viewModel.config.dataBits)
            assertEquals(2, viewModel.config.stopBitsIndex)
            assertEquals(1, viewModel.config.parityIndex)
            assertEquals(3, viewModel.config.flowControlIndex)

            viewModel.loadSimulator(invalid.toFile())
            assertFalse(viewModel.isSimulating)
            assertTrue(viewModel.configIssues.isNotEmpty())
        } finally {
            Files.deleteIfExists(valid)
            Files.deleteIfExists(invalid)
        }
    }

    @Test
    fun logsAndCountersCanBeBoundedAndCleared() = withViewModel { viewModel ->
        repeat(1_010) { viewModel.addLog("entry-$it", LogType.INFO) }
        assertEquals(1_000, viewModel.logs.size)
        assertEquals("entry-10", viewModel.logs.first().content)
        viewModel.txCount = 12
        viewModel.rxCount = 34
        viewModel.clearTx()
        viewModel.clearRx()
        assertEquals(0L, viewModel.txCount)
        assertEquals(0L, viewModel.rxCount)
        viewModel.clearLogs()
        assertTrue(viewModel.logs.isEmpty())
    }

    private fun withViewModel(block: (SerialViewModel) -> Unit) {
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            block(viewModel)
        } finally {
            viewModel.onCleared()
        }
    }
}
