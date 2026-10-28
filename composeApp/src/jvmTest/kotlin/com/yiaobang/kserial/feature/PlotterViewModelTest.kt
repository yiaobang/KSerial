package com.yiaobang.kserial.feature

import com.yiaobang.kserial.model.SerialLineSignals
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.SerialTransportEvent
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 绘图工作区状态机：用内存传输验证"数据行 → 采样缓冲 → 窗口/暂停/清空"的完整行为，
 * 不需要真实串口；真实链路由 PlotterHardwareTest 覆盖。
 */
class PlotterViewModelTest {

    @AfterTest
    fun cleanupGlobals() {
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun openWithoutPortLogsAndStaysClosed() = runTest {
        val vm = PlotterViewModel(FakeSerial(), monitorPortLoss = false)
        vm.open()
        assertFalse(vm.isOpen)
        assertTrue(vm.logs.isNotEmpty(), "blank address should be reported in the log")
        vm.onCleared()
    }

    @Test
    fun incomingLinesBecomeMultiChannelSamples() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()
        assertTrue(vm.isOpen)

        transport.push("TEMP=23.5,HUM=61\r\n".encodeToByteArray())
        transport.push("24.0\r\n".encodeToByteArray())

        val samples = vm.samples()
        assertEquals(2, samples.size)
        assertEquals(2, vm.channelCount)
        assertTrue(vm.revision > 0, "new samples must bump the redraw revision")
        assertContentEquals(doubleArrayOf(23.5, 61.0), samples.first())
        assertContentEquals(doubleArrayOf(24.0), samples.last())
        vm.onCleared()
    }

    @Test
    fun windowSizeTrimsOldestSamples() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()
        vm.setWindow(50)

        repeat(60) { index -> transport.push("$index\n".encodeToByteArray()) }

        val samples = vm.samples()
        assertEquals(50, samples.size)
        assertContentEquals(doubleArrayOf(10.0), samples.first())
        assertContentEquals(doubleArrayOf(59.0), samples.last())
        vm.onCleared()
    }

    @Test
    fun pauseDropsIncomingSamplesUntilResumed() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()

        transport.push("1.0\n".encodeToByteArray())
        assertEquals(1, vm.samples().size)

        vm.togglePause()
        assertTrue(vm.paused)
        transport.push("2.0\n".encodeToByteArray())
        assertEquals(1, vm.samples().size, "paused plotter must not buffer new samples")

        vm.togglePause()
        transport.push("3.0\n".encodeToByteArray())
        assertEquals(2, vm.samples().size)
        assertContentEquals(doubleArrayOf(3.0), vm.samples().last())
        vm.onCleared()
    }

    @Test
    fun clearEmptiesBufferButKeepsListening() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()

        transport.push("1.0\n".encodeToByteArray())
        vm.clear()
        assertEquals(0, vm.samples().size)
        assertEquals(0, vm.channelCount)
        assertEquals(0, vm.droppedLines)

        transport.push("2.0\n".encodeToByteArray())
        assertEquals(1, vm.samples().size, "clear must not close the port or remove the listener")
        vm.onCleared()
    }

    @Test
    fun closeRemovesListenerAndReleasesTheLease() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()
        assertTrue(SerialPortLeaseRegistry.acquire("COM1", "competing-owner").not(), "open plotter should hold the lease")

        vm.close()
        assertFalse(vm.isOpen)
        transport.push("9.9\n".encodeToByteArray())
        assertEquals(0, vm.samples().size, "closed plotter must ignore late data")
        assertTrue(SerialPortLeaseRegistry.acquire("COM1", "competing-owner"), "close must release the port lease")
        SerialPortLeaseRegistry.release("COM1", "competing-owner")
        vm.onCleared()
    }

    @Test
    fun bytesWithoutNewlineStayPendingAndAreCounted() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()

        // 发送端没带行尾：字节收到了，但永远不会变成采样——必须能从状态上看出来。
        transport.push("123".encodeToByteArray())
        assertEquals(0, vm.samples().size, "incomplete line must not become a sample")
        assertEquals(3L, vm.receivedBytes)
        assertEquals(3, vm.pendingBytes, "bytes waiting for a newline must be reported")

        transport.push("\n".encodeToByteArray())
        assertEquals(1, vm.samples().size, "the newline completes the pending line")
        assertEquals(0, vm.pendingBytes)

        vm.clear()
        assertEquals(0L, vm.receivedBytes, "clear resets the receive statistics too")
        vm.onCleared()
    }

    @Test
    fun pauseStillCountsBytesButDropsSamples() = runTest {
        val transport = FakeSerial()
        val vm = PlotterViewModel(transport, monitorPortLoss = false)
        vm.port = "COM1"
        vm.open()
        vm.togglePause()

        transport.push("42\n".encodeToByteArray())
        assertEquals(0, vm.samples().size, "paused plotter must not buffer samples")
        assertEquals(3L, vm.receivedBytes, "byte counter keeps working while paused")
        vm.onCleared()
    }

    /**
     * 绘图页地址框现在也接受网络端点：用真实 TCP 服务端验证
     * "填 tcp:// → 打开 → 收到的文本行变成曲线"，以及网络端点不会被端口枚举误判掉线。
     */
    @Test
    fun networkEndpointOpensAndPlotsRemoteNumericLines() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val out = socket.getOutputStream()
                    out.write("1,2\n3,5\n".toByteArray())
                    out.flush()
                    Thread.sleep(500)
                }
            }
        }
        val vm = PlotterViewModel(monitorPortLoss = false)
        try {
            vm.selectPort("tcp://127.0.0.1:${server.localPort}")
            vm.open()
            assertTrue(vm.isOpen, "TransportRouter should open a network address for the plotter")

            val deadline = System.currentTimeMillis() + 3_000
            while (vm.samples().size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)

            val samples = vm.samples()
            assertEquals(2, samples.size, "both received lines should become samples")
            assertContentEquals(doubleArrayOf(1.0, 2.0), samples[0])
            assertContentEquals(doubleArrayOf(3.0, 5.0), samples[1])
            assertEquals(2, vm.channelCount)
            assertEquals("tcp://127.0.0.1:${server.localPort}", vm.port)
        } finally {
            vm.onCleared()
            runCatching { server.close() }
        }
    }

    /** 内存传输：记录写入、允许测试手动注入"串口收到"的数据。 */
    private class FakeSerial : SerialTransport {
        val writes = mutableListOf<ByteArray>()
        private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
        private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

        override val lastErrorMessage: String? = null
        override val isOpen: Boolean = false

        override fun addDataListener(listener: (ByteArray) -> Unit) { dataListeners += listener }
        override fun removeDataListener(listener: (ByteArray) -> Unit) { dataListeners -= listener }
        override fun clearDataListeners() = dataListeners.clear()
        override fun addTransportListener(listener: (SerialTransportEvent) -> Unit) { transportListeners += listener }
        override fun removeTransportListener(listener: (SerialTransportEvent) -> Unit) { transportListeners -= listener }
        override fun clearTransportListeners() = transportListeners.clear()

        override fun openPort(
            portName: String,
            baudRate: Int,
            dataBits: Int,
            stopBitsIndex: Int,
            parityIndex: Int,
            flowControlIndex: Int
        ): Boolean = true

        override fun closePort() {}
        override fun setDtr(enabled: Boolean): Boolean = false
        override fun setRts(enabled: Boolean): Boolean = false
        override fun setBreak(enabled: Boolean): Boolean = false
        override fun lineSignals(): SerialLineSignals = SerialLineSignals()
        override fun write(data: ByteArray): Int {
            writes += data.copyOf()
            return data.size
        }

        fun push(data: ByteArray) {
            dataListeners.forEach { it(data) }
        }
    }
}
