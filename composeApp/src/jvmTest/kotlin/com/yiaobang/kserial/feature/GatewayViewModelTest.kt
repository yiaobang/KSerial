package com.yiaobang.kserial.feature

import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SerialLineSignals
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.SerialTransportEvent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayViewModelTest {
    @Test
    fun loadingConfigurationDoesNotOpenEndpointsUntilRun() {
        val config = Files.createTempFile("kserial-gateway-load", ".conf")
        val viewModel = GatewayViewModel()
        try {
            Files.writeString(config, """
                gateway {
                  endpoints = [
                    { id = "source", name = "Source", port = "COM91", baudRate = 9600 },
                    { id = "target", name = "Target", port = "COM92", baudRate = 9600 }
                  ]
                  routes = [
                    { sources = ["source"], targets = ["target"] }
                  ]
                }
            """.trimIndent())

            viewModel.loadConfig(config.toFile())

            assertEquals(2, viewModel.endpoints.size)
            assertTrue(viewModel.endpoints.none { it.isOpen })
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
            Files.deleteIfExists(config)
        }
    }

    @Test
    fun invalidConfigurationClearsPreviouslyLoadedBridge() {
        val valid = Files.createTempFile("kserial-gateway-valid", ".conf")
        val invalid = Files.createTempFile("kserial-gateway-invalid", ".conf")
        val viewModel = GatewayViewModel()
        try {
            Files.writeString(valid, """
                gateway {
                  endpoints = [
                    { id = "source", port = "COM91", baudRate = 9600 },
                    { id = "target", port = "COM92", baudRate = 9600 }
                  ]
                  routes = [{ sources = ["source"], targets = ["target"] }]
                }
            """.trimIndent())
            Files.writeString(invalid, """
                gateway {
                  endpoints = [
                    { id = "source", port = "COM91", baudRate = 9600 },
                    { id = "target", port = "COM92", baudRate = 9600 }
                  ]
                }
            """.trimIndent())

            viewModel.loadConfig(valid.toFile())
            assertEquals(2, viewModel.endpoints.size)

            viewModel.loadConfig(invalid.toFile())

            assertTrue(viewModel.endpoints.isEmpty())
            assertTrue(viewModel.routes.isEmpty())
            assertTrue(viewModel.configIssues.isNotEmpty())
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
            Files.deleteIfExists(valid)
            Files.deleteIfExists(invalid)
        }
    }

    @Test
    fun forwardingLogContainsOnlyTheBytesWrittenToTheTarget() = runTest {
        val targetTransport = RecordingTransport()
        val source = GatewayEndpoint(0, "source", "Source", RecordingTransport())
        val target = GatewayEndpoint(1, "target", "Target", targetTransport).apply { isOpen = true }
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += target
            routes += GatewayRoute("route", setOf(source.id), setOf(target.id), enabled = true)
        }

        try {
            val payload = byteArrayOf(0x01, 0x02, 0xFF.toByte())
            viewModel.onEndpointData(source, payload)

            assertContentEquals(payload, targetTransport.writes.single())
            assertTrue(viewModel.logs.none { it.type == LogType.RX })
            val transmitted = viewModel.logs.single { it.type == LogType.TX }
            assertTrue(transmitted.content.contains("Source → Target"))
            assertTrue(transmitted.content.contains("01 02 FF"))
            assertEquals(payload.size.toLong(), target.txCount)
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun forwardingIsLoggedOnceWithAllTargetsWhenMerged() = runTest {
        val targets = (1..3).map { i ->
            GatewayEndpoint(i, "target$i", "Target$i", RecordingTransport()).apply { isOpen = true }
        }
        val source = GatewayEndpoint(0, "source", "Source", RecordingTransport())
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += targets
            routes += GatewayRoute("route", setOf(source.id), targets.map { it.id }.toSet(), enabled = true)
        }

        try {
            viewModel.onEndpointData(source, byteArrayOf(0x41, 0x42))

            assertEquals(1, viewModel.logs.count { it.type == LogType.TX })
            val line = viewModel.logs.single { it.type == LogType.TX }.content
            targets.forEach { assertTrue(line.contains(it.name)) }
            assertTrue(line.contains("AB"))
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun perTargetLoggingStillAvailableWhenMergeDisabled() = runTest {
        val targets = (1..3).map { i ->
            GatewayEndpoint(i, "target$i", "Target$i", RecordingTransport()).apply { isOpen = true }
        }
        val source = GatewayEndpoint(0, "source", "Source", RecordingTransport())
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += targets
            routes += GatewayRoute("route", setOf(source.id), targets.map { it.id }.toSet(), enabled = true)
            mergeForwardedLogs = false
        }

        try {
            viewModel.onEndpointData(source, byteArrayOf(0x41))

            assertEquals(targets.size, viewModel.logs.count { it.type == LogType.TX })
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun signalForwardingMirrorsCtsAndDsrToRouteTargets() = runTest {
        val sourceTransport = RecordingTransport()
        val targetTransport = RecordingTransport()
        val source = GatewayEndpoint(0, "source", "Source", sourceTransport).apply { isOpen = true }
        val target = GatewayEndpoint(1, "target", "Target", targetTransport).apply { isOpen = true }
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += target
            routes += GatewayRoute("route", setOf(source.id), setOf(target.id), enabled = true)
            forwardSignals = true
        }

        try {
            sourceTransport.signals = SerialLineSignals(cts = false, dsr = false)
            viewModel.syncLineSignals()
            targetTransport.rtsWrites.single()
            targetTransport.dtrWrites.single()

            sourceTransport.signals = SerialLineSignals(cts = true, dsr = true)
            viewModel.syncLineSignals()
            assertTrue(targetTransport.rtsWrites.last())
            assertTrue(targetTransport.dtrWrites.last())

            // 电平不变时不应重复写目标。
            assertEquals(2, targetTransport.rtsWrites.size)
            assertEquals(2, targetTransport.dtrWrites.size)
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun dropModeCountsDroppedBytesWhenQueueOverflows() = runTest {
        val source = GatewayEndpoint(0, "source", "Source", RecordingTransport())
        val target = GatewayEndpoint(1, "target", "Target", RecordingTransport()).apply { isOpen = true }
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += target
            routes += GatewayRoute("route", setOf(source.id), setOf(target.id), enabled = true)
        }

        try {
            val chunk = ByteArray(2)
            repeat(600) { viewModel.onDataArrived(source, chunk) }

            assertTrue(source.droppedBytes.get() > 0)
            // 消费掉队列中剩余的帧，累计接收数应等于 600 帧。
            var received = 0
            while (!source.receiveQueue.isEmpty) {
                source.receiveQueue.receiveCatching().getOrNull() ?: break
                received++
            }
            assertEquals(600 * chunk.size.toLong(), source.droppedBytes.get() + received * chunk.size)
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun blockModeQueuesEverythingWithoutDropping() = runTest {
        val source = GatewayEndpoint(0, "source", "Source", RecordingTransport())
        val target = GatewayEndpoint(1, "target", "Target", RecordingTransport()).apply { isOpen = true }
        val viewModel = GatewayViewModel().apply {
            endpoints += source
            endpoints += target
            routes += GatewayRoute("route", setOf(source.id), setOf(target.id), enabled = true)
            blockBackpressure = true
        }

        try {
            val chunk = ByteArray(2)
            // 少于队列容量时 block 模式应全部入队、零丢弃。
            repeat(400) { viewModel.onDataArrived(source, chunk) }

            assertEquals(0L, source.droppedBytes.get())
            var received = 0
            while (!source.receiveQueue.isEmpty) {
                source.receiveQueue.receiveCatching().getOrNull() ?: break
                received++
            }
            assertEquals(400, received)
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun onClearedCancelsViewModelScope() {
        val viewModel = GatewayViewModel()
        try {
            assertTrue(viewModel.viewModelScope.isActive)
            viewModel.onCleared()
            assertFalse(viewModel.viewModelScope.isActive)
        } finally {
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }
}

private class RecordingTransport : SerialTransport {
    private var open = false
    private val dataListeners = mutableListOf<(ByteArray) -> Unit>()
    private val transportListeners = mutableListOf<(SerialTransportEvent) -> Unit>()

    val writes = mutableListOf<ByteArray>()
    @Volatile var signals = SerialLineSignals()
    val rtsWrites = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
    val dtrWrites = java.util.concurrent.CopyOnWriteArrayList<Boolean>()

    override val lastErrorMessage: String? = null
    override val isOpen: Boolean
        get() = open

    override fun addDataListener(listener: (ByteArray) -> Unit) {
        dataListeners += listener
    }

    override fun removeDataListener(listener: (ByteArray) -> Unit) {
        dataListeners -= listener
    }

    override fun clearDataListeners() {
        dataListeners.clear()
    }

    override fun addTransportListener(listener: (SerialTransportEvent) -> Unit) {
        transportListeners += listener
    }

    override fun removeTransportListener(listener: (SerialTransportEvent) -> Unit) {
        transportListeners -= listener
    }

    override fun clearTransportListeners() {
        transportListeners.clear()
    }

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

    override fun setDtr(enabled: Boolean): Boolean {
        dtrWrites += enabled
        return true
    }

    override fun setRts(enabled: Boolean): Boolean {
        rtsWrites += enabled
        return true
    }
    override fun setBreak(enabled: Boolean): Boolean = true
    override fun lineSignals(): SerialLineSignals = signals

    override fun write(data: ByteArray): Int {
        writes += data.copyOf()
        return data.size
    }
}
