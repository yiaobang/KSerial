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
            assertTrue(transmitted.content.contains("0102FF"))
            assertEquals(payload.size.toLong(), target.txCount)
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

    override fun setDtr(enabled: Boolean): Boolean = true
    override fun setRts(enabled: Boolean): Boolean = true
    override fun setBreak(enabled: Boolean): Boolean = true
    override fun lineSignals(): SerialLineSignals = SerialLineSignals()

    override fun write(data: ByteArray): Int {
        writes += data.copyOf()
        return data.size
    }
}
