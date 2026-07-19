package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.GatewayMode
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 使用五组虚拟串口验证桥接：奇数端口模拟外部设备，偶数端口由桥接端点打开。
 * 所有断言都从外部端口实际写入并在目标外部端口读取，不直接调用内部路由方法。
 */
class GatewayHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun fivePairGatewayCoversConfiguredAndDynamicRoutingModes() {
        HardwareTestSupport.requireHardware(pairCount = 5)
        val externalPorts = HardwareTestSupport.pairs.map(Pair<String, String>::first)
        val gatewayPorts = HardwareTestSupport.pairs.map(Pair<String, String>::second)
        val peers = mutableListOf<SerialComm>()
        val config = HardwareTestSupport.tempConfig("kserial-five-port-gateway", gatewayConfig(gatewayPorts))
        val viewModel = GatewayViewModel()
        try {
            externalPorts.forEach { peers += HardwareTestSupport.open(it) }
            viewModel.loadConfig(config.toFile())
            assertEquals(5, viewModel.endpoints.size)
            viewModel.endpoints.forEach(viewModel::openEndpoint)
            assertTrue(viewModel.endpoints.all { it.isOpen })

            val fanOutPayload = "fan-out".encodeToByteArray()
            val fanOutCaptures = (1..4).associateWith { index ->
                SerialCapture(fanOutPayload.size).also { peers[index].addDataListener(it.listener) }
            }
            assertEquals(
                fanOutPayload.size,
                HardwareTestSupport.writeAndTrace(peers[0], externalPorts[0], "bridge fan-out", fanOutPayload),
                "Fan-out source did not send the full payload"
            )
            fanOutCaptures.forEach { (index, capture) ->
                val received = capture.await()
                HardwareTestSupport.traceReceived(externalPorts[index], received)
                assertContentEquals(fanOutPayload, received)
            }

            val replyPayload = "reply".encodeToByteArray()
            val replyCapture = SerialCapture(replyPayload.size)
            peers[0].addDataListener(replyCapture.listener)
            assertEquals(
                replyPayload.size,
                HardwareTestSupport.writeAndTrace(peers[1], externalPorts[1], externalPorts[0], replyPayload),
                "Reply source did not send the full payload"
            )
            val replyReceived = replyCapture.await()
            HardwareTestSupport.traceReceived(externalPorts[0], replyReceived)
            assertContentEquals(replyPayload, replyReceived)

            val disabledPayload = "disabled".encodeToByteArray()
            val disabledCapture = SerialCapture(disabledPayload.size)
            peers[1].addDataListener(disabledCapture.listener)
            assertEquals(
                disabledPayload.size,
                HardwareTestSupport.writeAndTrace(peers[4], externalPorts[4], "disabled route", disabledPayload),
                "Disabled-route source did not send the full payload"
            )
            assertFalse(disabledCapture.receivedWithin(500), "Disabled route unexpectedly forwarded data")
            assertEndpointCounters(
                viewModel,
                // fan-out: endpoint0 RX 7，endpoint1..4 各 TX 7；reply 与 disabled 再分别累加。
                expectedRx = listOf(7L, 5L, 0L, 0L, 8L),
                expectedTx = listOf(5L, 7L, 7L, 7L, 7L)
            )

            viewModel.routes.clear()
            viewModel.clearCounters()
            viewModel.mode = GatewayMode.OneToOne
            viewModel.sourceIndex = 0
            viewModel.targetIndex = 2
            assertRoute(peers, externalPorts, sourceIndex = 0, targetIndices = listOf(2), payload = "one-to-one".encodeToByteArray())

            viewModel.mode = GatewayMode.OneToMany
            viewModel.sourceIndex = 1
            assertRoute(peers, externalPorts, sourceIndex = 1, targetIndices = listOf(0, 2, 3, 4), payload = "one-to-many".encodeToByteArray())

            viewModel.mode = GatewayMode.ManyToMany
            assertRoute(
                peers,
                externalPorts,
                sourceIndex = 3,
                targetIndices = listOf(0, 1, 2, 4),
                // 使用 0x00..0xFF 验证桥接对二进制数据透明，不只验证可打印文本。
                payload = ByteArray(256) { it.toByte() }
            )

            assertEndpointCounters(
                viewModel,
                // 精确值来自三种动态模式的累计结果，可同时发现漏转发、重复转发和计数错误。
                expectedRx = listOf(10L, 11L, 0L, 256L, 0L),
                expectedTx = listOf(267L, 256L, 277L, 11L, 267L)
            )

            assertFalse(SerialPortLeaseRegistry.acquire(gatewayPorts[0].lowercase(), "competing-owner"))
        } finally {
            viewModel.onCleared()
            peers.forEach(SerialComm::closePort)
            Files.deleteIfExists(config)
        }
    }

    private fun assertRoute(
        peers: List<SerialComm>,
        portNames: List<String>,
        sourceIndex: Int,
        targetIndices: List<Int>,
        payload: ByteArray
    ) {
        val captures = targetIndices.associateWith { index ->
            SerialCapture(payload.size).also { peers[index].addDataListener(it.listener) }
        }
        val unexpectedCaptures = peers.indices
            // 除源端和目标端外全部监听，确保一对一路由不会把数据泄漏到其他端点。
            .filter { it != sourceIndex && it !in targetIndices }
            .associateWith { index ->
                SerialCapture(payload.size).also { peers[index].addDataListener(it.listener) }
            }
        assertEquals(
            payload.size,
            HardwareTestSupport.writeAndTrace(peers[sourceIndex], portNames[sourceIndex], "bridge targets ${targetIndices.joinToString()}", payload),
            "Gateway source did not send the full payload"
        )
        captures.forEach { (index, capture) ->
            val received = capture.await()
            HardwareTestSupport.traceReceived(portNames[index], received)
            assertContentEquals(payload, received)
        }
        unexpectedCaptures.forEach { (index, capture) ->
            assertFalse(capture.receivedWithin(300), "Payload leaked to non-target endpoint $index")
        }
    }

    private fun assertEndpointCounters(
        viewModel: GatewayViewModel,
        expectedRx: List<Long>,
        expectedTx: List<Long>
    ) {
        HardwareTestSupport.eventually {
            viewModel.endpoints.map { it.rxCount } == expectedRx &&
                viewModel.endpoints.map { it.txCount } == expectedTx
        }
        assertEquals(expectedRx, viewModel.endpoints.map { it.rxCount }, "Unexpected bridge RX counters")
        assertEquals(expectedTx, viewModel.endpoints.map { it.txCount }, "Unexpected bridge TX counters")
    }

    private fun gatewayConfig(ports: List<String>): String = """
        gateway {
          endpoints = [
            { id = "endpoint0", name = "Endpoint 0", port = "${ports[0]}", baudRate = 115200 },
            { id = "endpoint1", name = "Endpoint 1", port = "${ports[1]}", baudRate = 115200 },
            { id = "endpoint2", name = "Endpoint 2", port = "${ports[2]}", baudRate = 115200 },
            { id = "endpoint3", name = "Endpoint 3", port = "${ports[3]}", baudRate = 115200 },
            { id = "endpoint4", name = "Endpoint 4", port = "${ports[4]}", baudRate = 115200 }
          ]
          routes = [
            { name = "fan-out", sources = ["endpoint0"], targets = ["endpoint1", "endpoint2", "endpoint3", "endpoint4"] },
            { name = "reply", sources = ["endpoint1"], targets = ["endpoint0"] },
            { name = "disabled", sources = ["endpoint4"], targets = ["endpoint1"], enabled = false }
          ]
        }
    """
}
