package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.CaptureViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用两组虚拟串口验证录制工作区：pair0 模拟"上位机 ⇄ 软件侧"，
 * pair1 模拟"设备侧 ⇄ 回声设备"。所有断言都从外部端口实际读写。
 * 重点回归：录制页必须把串口原始数据接进消费队列，否则真实运行时收不到任何字节。
 */
class CaptureHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun dualPortCaptureForwardsAndRecordsPairs() {
        HardwareTestSupport.requireHardware(pairCount = 2)
        val softwareExternal = HardwareTestSupport.pairs[0].first
        val softwareCapture = HardwareTestSupport.pairs[0].second
        val deviceExternal = HardwareTestSupport.pairs[1].first
        val deviceCapture = HardwareTestSupport.pairs[1].second

        // 回声设备：收到完整一行后把 "ECHO:" 前缀的原数据发回来。
        val responder = HardwareTestSupport.open(deviceExternal)
        val requestBuffer = StringBuilder()
        responder.addDataListener { chunk ->
            synchronized(requestBuffer) {
                requestBuffer.append(String(chunk))
                if (requestBuffer.contains("\r\n")) {
                    val request = requestBuffer.toString()
                    requestBuffer.clear()
                    responder.write("ECHO:$request".encodeToByteArray())
                }
            }
        }

        val viewModel = CaptureViewModel()
        viewModel.softwareSide.selectedPort = softwareCapture
        viewModel.deviceSide.selectedPort = deviceCapture
        viewModel.startForwarding()
        assertTrue(viewModel.forwarding, "Capture forwarding failed to start")
        viewModel.startRecording()

        val operator = HardwareTestSupport.open(softwareExternal)
        val command = "AT+PING\r\n"
        val reply = SerialCapture("ECHO:$command".length)
        operator.addDataListener(reply.listener)
        assertEquals(
            command.length,
            HardwareTestSupport.writeAndTrace(operator, softwareExternal, deviceCapture, command.encodeToByteArray()),
            "Operator software did not send the full command"
        )
        val received = reply.await()
        HardwareTestSupport.traceReceived(softwareExternal, received)
        assertContentEquals("ECHO:$command".encodeToByteArray(), received)

        // 转发链路畅通后，问答对应被录制成 1 组
        HardwareTestSupport.eventually { viewModel.recordedPairCount == 1 }
        assertEquals(1, viewModel.recordedPairCount)

        viewModel.stopForwarding()
    }
}
