package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 混合拓扑：桥接的一端是真实虚拟串口、另一端是 TCP 服务端。
 * 验证 TransportRouter 在真实串口 + 网络并存时的路由、计数和双向透传。
 */
class NetworkSerialBridgeHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun tcpClientsTalkThroughTheBridgeToSerialPeers() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val serialExternal = HardwareTestSupport.pairs[0].first
        val serialGateway = HardwareTestSupport.pairs[0].second
        val tcpPort = ServerSocket(0).use { it.localPort }

        val peer = HardwareTestSupport.open(serialExternal, baudRate = 9600)
        val config = HardwareTestSupport.tempConfig("kserial-net-bridge", """
            gateway {
              endpoints = [
                { id = "net", name = "TCP Server", port = "tcp-listen://$tcpPort" },
                { id = "com", name = "Serial", port = "$serialGateway", baudRate = 9600 }
              ]
              routes = [
                { name = "to-serial", sources = ["net"], targets = ["com"] },
                { name = "to-net", sources = ["com"], targets = ["net"] }
              ]
            }
        """)

        val viewModel = GatewayViewModel()
        var client: Socket? = null
        try {
            viewModel.loadConfig(config.toFile())
            assertEquals(2, viewModel.endpoints.size)
            viewModel.endpoints.forEach(viewModel::openEndpoint)
            assertTrue(viewModel.endpoints.all { it.isOpen }, "Both mixed endpoints must open")

            client = HardwareTestSupport.connectWithRetry(tcpPort)
            // TCP 服务端在后台 accept；即使立刻写，字节也会在 OS 缓冲里等 accept 完成，不会丢。

            // TCP → 串口
            val downlink = "HELLO-SERIAL".encodeToByteArray()
            val peerCapture = SerialCapture(downlink.size)
            peer.addDataListener(peerCapture.listener)
            client.getOutputStream().apply { write(downlink); flush() }
            val atPeer = peerCapture.await()
            HardwareTestSupport.traceReceived(serialExternal, atPeer)
            assertContentEquals(downlink, atPeer)

            // 串口 → TCP
            val uplink = "WORLD-TCP".encodeToByteArray()
            val netReceived = CopyOnWriteArrayList<ByteArray>()
            client.getInputStream().let { stream ->
                val reader = Thread {
                    val buffer = ByteArray(uplink.size)
                    var offset = 0
                    while (offset < buffer.size) {
                        val read = stream.read(buffer, offset, buffer.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                    netReceived += buffer.copyOf(offset)
                }
                reader.isDaemon = true
                reader.start()
                assertEquals(
                    uplink.size,
                    HardwareTestSupport.writeAndTrace(peer, serialExternal, serialGateway, uplink),
                    "Serial peer did not send the reply fully"
                )
                reader.join(5_000)
            }
            HardwareTestSupport.traceReceived("tcp-client", uplink)
            assertContentEquals(uplink, netReceived.singleOrNull(), "TCP client must receive the serial reply")

            HardwareTestSupport.eventually {
                val rx = viewModel.endpoints.map { it.rxCount }
                val tx = viewModel.endpoints.map { it.txCount }
                rx[0] == downlink.size.toLong() && tx[1] == downlink.size.toLong() &&
                    rx[1] == uplink.size.toLong() && tx[0] == uplink.size.toLong()
            }
        } finally {
            client?.close()
            viewModel.onCleared()
            peer.closePort()
            Files.deleteIfExists(config)
        }
    }
}
