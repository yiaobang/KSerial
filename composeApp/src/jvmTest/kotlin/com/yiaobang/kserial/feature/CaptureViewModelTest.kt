package com.yiaobang.kserial.feature

import com.yiaobang.kserial.model.SerialLineSignals
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.SerialTransportEvent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureViewModelTest {

    @Test
    fun softwareCommandAndDeviceReplyBecomeOnePair() = runTest {
        val software = FakeTransport()
        val device = FakeTransport()
        val vm = capture(software, device)

        vm.startRecording()
        vm.forward(vm.softwareSide, vm.deviceSide, "AT+VERSION\r\n".toByteArray())
        vm.forward(vm.deviceSide, vm.softwareSide, "KSerial 1.0\r\n".toByteArray())

        // 双向透传：字节原样到达对端
        assertEquals(listOf("AT+VERSION\r\n"), device.writes.map { String(it) })
        assertEquals(listOf("KSerial 1.0\r\n"), software.writes.map { String(it) })
        assertEquals(1, vm.recordedPairCount)
        assertEquals("AT+VERSION\r\n".toByteArray().size.toLong(), vm.bytesToDevice)
        assertEquals("KSerial 1.0\r\n".toByteArray().size.toLong(), vm.bytesToSoftware)
    }

    @Test
    fun deviceInitiatedDataIsNotTreatedAsACommand() = runTest {
        val software = FakeTransport()
        val device = FakeTransport()
        val vm = capture(software, device)

        vm.startRecording()
        // 设备主动上报：没有对应请求，配不出规则，因此不应该产生问答对
        vm.forward(vm.deviceSide, vm.softwareSide, "BOOT OK\r\n".toByteArray())

        assertEquals(0, vm.recordedPairCount)
        assertContentEquals("BOOT OK\r\n".toByteArray(), software.writes.single())
    }

    @Test
    fun failedForwardStopsForwardingAndReportsTarget() = runTest {
        val software = FakeTransport()
        val device = FakeTransport(failWrites = true)
        val vm = capture(software, device)

        vm.forward(vm.softwareSide, vm.deviceSide, "AT\r\n".toByteArray())

        // 失败日志的文案随语言变化，这里只断言"报了错"，不锁定具体文字。
        assertTrue(vm.logs.any { it.content.contains("→") })
    }

    @Test
    fun manualModeSendsToSinglePortAndPairsReply() = runTest {
        val software = FakeTransport()
        val device = FakeTransport()
        val vm = capture(software, device)
        vm.setManualCapture(true)
        // 手动模式不要求软件侧地址
        assertTrue(vm.softwareSide.selectedPort.isBlank())
        vm.deviceSide.selectedPort = "COM8"
        assertTrue(vm.canStartForwarding)

        vm.startRecording()
        vm.manualSend("AT+PING\r\n".toByteArray())
        vm.receiveFromDevice("PONG".toByteArray())

        assertEquals(listOf("AT+PING\r\n"), device.writes.map { String(it) })
        assertEquals(emptyList(), software.writes)
        assertEquals(1, vm.recordedPairCount)
        assertEquals("AT+PING\r\n".toByteArray().size.toLong(), vm.bytesToDevice)
        assertEquals(4L, vm.bytesToSoftware)
    }

    @Test
    fun manualPayloadUsesLineEndingAndHex() = runTest {
        val vm = capture(FakeTransport(), FakeTransport())

        vm.manualInput = "AT"
        vm.manualLineEnding = com.yiaobang.kserial.model.SendLineEnding.CRLF
        assertContentEquals("AT\r\n".toByteArray(), vm.buildManualPayload())

        vm.manualHexMode = true
        vm.manualInput = "01 A0 FF"
        assertContentEquals(byteArrayOf(0x01, 0xA0.toByte(), 0xFF.toByte()), vm.buildManualPayload())

        // 非法 HEX 返回 null 并写日志提示
        vm.manualInput = "GG"
        assertEquals(null, vm.buildManualPayload())
        assertTrue(vm.logs.isNotEmpty())
    }

    @Test
    fun serialDataListenerFeedsReceiveQueueForForwarding() = runTest {
        // 回归测试：录制页曾漏接串口数据监听，导致真实运行时收不到任何数据。
        val software = FakeTransport()
        val device = FakeTransport()
        val vm = capture(software, device)

        val payload = "ECHO\r\n".toByteArray()
        device.pushIncoming(payload)
        assertContentEquals(payload, vm.deviceSide.receiveQueue.tryReceive().getOrNull())

        software.pushIncoming(payload)
        assertContentEquals(payload, vm.softwareSide.receiveQueue.tryReceive().getOrNull())
    }

    private fun capture(software: FakeTransport, device: FakeTransport) = CaptureViewModel(
        listOf(
            GatewayEndpoint(index = 0, id = "software", name = "software", serialComm = software),
            GatewayEndpoint(index = 1, id = "device", name = "device", serialComm = device)
        )
    )

    private class FakeTransport(private val failWrites: Boolean = false) : SerialTransport {
        val writes = CopyOnWriteArrayList<ByteArray>()
        private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
        private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()
        private var open = true

        override val lastErrorMessage: String? = null
        override val isOpen: Boolean get() = open

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
        ): Boolean {
            open = true
            return true
        }

        override fun closePort() {
            open = false
        }

        override fun setDtr(enabled: Boolean): Boolean = true

        override fun setRts(enabled: Boolean): Boolean = true

        override fun setBreak(enabled: Boolean): Boolean = true

        override fun lineSignals(): SerialLineSignals = SerialLineSignals()

        override fun write(data: ByteArray): Int {
            if (failWrites) return -1
            writes += data.copyOf()
            return data.size
        }

        /** 模拟串口收到对端数据：触发 ViewModel 注册的数据监听器。 */
        fun pushIncoming(data: ByteArray) {
            dataListeners.forEach { it(data) }
        }
    }
}
