package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.CaptureViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.SimulatorConfigGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 端到端闭环：真实虚拟串口上录制真实设备 → 导出模拟器配置 → 加载模拟器复现设备行为。
 * pair0 模拟"上位机 ⇄ 软件侧"，pair1 模拟"设备侧 ⇄ 被录制的设备"。
 * 这条链路是把"只有一台真机"变成"团队可复现资产"的核心卖点，必须整体验证。
 */
class RecorderRoundTripHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun recordedPairsReproduceTheDeviceThroughTheSimulator() {
        HardwareTestSupport.requireHardware(pairCount = 2)
        val operatorExternal = HardwareTestSupport.pairs[0].first
        val softwareCapture = HardwareTestSupport.pairs[0].second
        val deviceExternal = HardwareTestSupport.pairs[1].first
        val deviceCapture = HardwareTestSupport.pairs[1].second

        // 被录制的"真实设备"：整行收到后原样加前缀回显。
        val device = HardwareTestSupport.open(deviceExternal)
        val requestBuffer = StringBuilder()
        device.addDataListener { chunk ->
            synchronized(requestBuffer) {
                requestBuffer.append(String(chunk))
                if (requestBuffer.contains("\r\n")) {
                    val request = requestBuffer.toString()
                    requestBuffer.clear()
                    device.write("ECHO:$request".encodeToByteArray())
                }
            }
        }

        val viewModel = CaptureViewModel()
        viewModel.softwareSide.selectedPort = softwareCapture
        viewModel.deviceSide.selectedPort = deviceCapture
        viewModel.startForwarding()
        assertTrue(viewModel.forwarding, "Capture forwarding failed to start")
        viewModel.startRecording()

        val operator = HardwareTestSupport.open(operatorExternal)
        val commands = listOf("AT+PING\r\n", "AT+VER\r\n")
        commands.forEach { command ->
            val expected = "ECHO:$command".encodeToByteArray()
            val capture = SerialCapture(expected.size)
            operator.addDataListener(capture.listener)
            assertEquals(
                command.length,
                HardwareTestSupport.writeAndTrace(operator, operatorExternal, deviceCapture, command.encodeToByteArray()),
                "Operator did not send '$command' fully"
            )
            val received = capture.await()
            HardwareTestSupport.traceReceived(operatorExternal, received)
            assertContentEquals(expected, received)
            // 录制端在移除旧监听前，这里的数据监听会持续累积；换命令前清掉。
            operator.removeDataListener(capture.listener)
        }

        viewModel.stopRecordingOnly()
        val pairs = viewModel.recordedPairs()
        assertEquals(2, pairs.size, "Both request/response pairs must be recorded")
        assertContentEquals("AT+PING\r\n".encodeToByteArray(), pairs[0].request)
        assertContentEquals("ECHO:AT+PING\r\n".encodeToByteArray(), pairs[0].response)

        // 录制端先放手，模拟器才能打开同一个端口。
        viewModel.stopForwarding()

        val config = HardwareTestSupport.tempConfig(
            "kserial-roundtrip-simulator",
            SimulatorConfigGenerator.generate(
                pairs,
                SimulatorConfigGenerator.EndpointInfo(
                    port = deviceCapture,
                    baudRate = viewModel.deviceSide.baudRate,
                    dataBits = viewModel.deviceSide.dataBits,
                    stopBitsIndex = viewModel.deviceSide.stopBitsIndex,
                    parityIndex = viewModel.deviceSide.parityIndex,
                    flowControlIndex = viewModel.deviceSide.flowControlIndex
                )
            )
        )
        val simulatorViewModel = com.yiaobang.kserial.SerialViewModel(
            autoConnectFirstPort = false,
            syncOnConfigChange = false
        )
        try {
            simulatorViewModel.loadSimulator(config.toFile())
            assertTrue(simulatorViewModel.isSimulating, "Generated config must load into the simulator")
            simulatorViewModel.togglePort()
            assertTrue(simulatorViewModel.isPortOpen)

            commands.forEach { command ->
                val expected = "ECHO:$command".encodeToByteArray()
                val capture = SerialCapture(expected.size)
                device.addDataListener(capture.listener)
                assertEquals(
                    command.length,
                    HardwareTestSupport.writeAndTrace(device, deviceExternal, deviceCapture, command.encodeToByteArray()),
                    "Replay did not send '$command' fully"
                )
                val received = capture.await()
                HardwareTestSupport.traceReceived(deviceExternal, received)
                assertContentEquals(expected, received, "Simulator replay must reproduce the recorded response")
                device.removeDataListener(capture.listener)
            }
        } finally {
            simulatorViewModel.onCleared()
            device.closePort()
            operator.closePort()
            java.nio.file.Files.deleteIfExists(config)
        }
    }

    /** 手动模式走一遍真实串口：页面输入区直接扮演上位机。 */
    @Test
    fun manualModeDrivesTheDeviceAndPairsReplies() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val deviceExternal = HardwareTestSupport.pairs[0].first
        val deviceCapture = HardwareTestSupport.pairs[0].second

        val device = HardwareTestSupport.open(deviceExternal)
        device.addDataListener { chunk ->
            if (String(chunk).contains("AT+PING")) {
                device.write("PONG".encodeToByteArray())
            }
        }

        val viewModel = CaptureViewModel()
        viewModel.setManualCapture(true)
        viewModel.deviceSide.selectedPort = deviceCapture
        assertTrue(viewModel.canStartForwarding, "Manual mode only needs the device-side address")
        viewModel.startForwarding()
        assertTrue(viewModel.forwarding)
        viewModel.startRecording()

        viewModel.manualInput = "AT+PING"
        viewModel.manualLineEnding = com.yiaobang.kserial.model.SendLineEnding.CRLF
        viewModel.sendManualInput()

        HardwareTestSupport.eventually {
            viewModel.recordedPairCount == 1 &&
                viewModel.bytesToSoftware == "PONG".encodeToByteArray().size.toLong()
        }
        assertEquals(1, viewModel.recordedPairCount)
        assertEquals("AT+PING\r\n".encodeToByteArray().size.toLong(), viewModel.bytesToDevice)
        assertEquals(4L, viewModel.bytesToSoftware)
        val pairs = viewModel.recordedPairs()
        assertEquals(1, pairs.size)
        assertContentEquals("AT+PING\r\n".encodeToByteArray(), pairs.single().request)
        assertContentEquals("PONG".encodeToByteArray(), pairs.single().response)

        viewModel.stopRecordingOnly()
        viewModel.stopForwarding()
        device.closePort()
    }
}
