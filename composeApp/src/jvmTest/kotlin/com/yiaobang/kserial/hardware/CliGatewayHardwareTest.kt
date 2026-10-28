package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.cli.runCli
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 无头 CLI 的真实链路：`gateway` 命令桥接"TCP 服务端 + 真实虚拟串口"，
 * 数据必须双向透传，--duration 到期后进程干净退出（退出码 0）。
 */
class CliGatewayHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun cliGatewayBridgesTcpAndSerialThenExitsCleanly() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val serialExternal = HardwareTestSupport.pairs[0].first
        val serialGateway = HardwareTestSupport.pairs[0].second
        val tcpPort = ServerSocket(0).use { it.localPort }

        val config = HardwareTestSupport.tempConfig("kserial-cli-gateway", """
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

        val peer = HardwareTestSupport.open(serialExternal, baudRate = 9600)
        val exitCode = AtomicInteger(Int.MIN_VALUE)
        val worker = thread(name = "kserial-cli") {
            exitCode.set(runCli(arrayOf("gateway", config.toFile().path, "--duration", "4")))
        }

        try {
            val client = HardwareTestSupport.connectWithRetry(tcpPort)
            // TCP → 串口
            val downlink = "PING-FROM-TCP".encodeToByteArray()
            val peerCapture = SerialCapture(downlink.size)
            peer.addDataListener(peerCapture.listener)
            client.getOutputStream().apply { write(downlink); flush() }
            val atPeer = peerCapture.await()
            HardwareTestSupport.traceReceived(serialExternal, atPeer)
            assertContentEquals(downlink, atPeer)

            // 串口 → TCP
            val uplink = "PONG-FROM-COM".encodeToByteArray()
            val netReceived = CopyOnWriteArrayList<ByteArray>()
            val reader = thread(name = "tcp-reader") {
                val buffer = ByteArray(uplink.size)
                var offset = 0
                while (offset < buffer.size) {
                    val read = client.getInputStream().read(buffer, offset, buffer.size - offset)
                    if (read < 0) break
                    offset += read
                }
                if (offset > 0) netReceived += buffer.copyOf(offset)
            }
            assertEquals(
                uplink.size,
                HardwareTestSupport.writeAndTrace(peer, serialExternal, serialGateway, uplink),
                "Serial peer did not send the reply fully"
            )
            reader.join(5_000)
            HardwareTestSupport.traceReceived("tcp-client", uplink)
            assertContentEquals(uplink, netReceived.singleOrNull(), "CLI gateway must forward serial data back to TCP")

            client.close()
            // --duration 4s：留给上面数据交换的时间绰绰有余，之后必须退出 0。
            worker.join(15_000)
            assertEquals(0, exitCode.get(), "CLI must exit with code 0 after the configured duration")
        } finally {
            worker.interrupt()
            peer.closePort()
            Files.deleteIfExists(config)
        }
    }
}
