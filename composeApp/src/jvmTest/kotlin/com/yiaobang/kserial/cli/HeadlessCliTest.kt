package com.yiaobang.kserial.cli

import com.yiaobang.kserial.serial.TcpTransport
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CLI 冒烟测试：用本机 TCP 端点替代真实串口，验证命令解析、配置加载和转发链路。
 */
class HeadlessCliTest {

    @Test
    fun helpExitsWithZero() {
        assertEquals(0, runCli(arrayOf("--help")))
    }

    @Test
    fun unknownCommandIsUsageError() {
        assertEquals(2, runCli(arrayOf("nope")))
    }

    @Test
    fun missingConfigFails() {
        assertEquals(1, runCli(arrayOf("gateway", "does-not-exist.conf", "--duration", "1")))
    }

    @Test
    fun testCommandRunsAgainstTcpEndpoint() {
        val port = freePort()
        val server = TcpTransport()
        assertTrue(server.openPort("tcp-listen://$port", 9600, 8, 0, 0, 0))
        server.addDataListener { server.write("OK\r\n".toByteArray()) }

        val config = tempConfig(
            """
            test {
              connection { port = "tcp://127.0.0.1:$port", baudRate = 115200 }
              defaults { timeout = 2000, interval = 0 }
              steps = [
                { name = "握手", send = "AT\r\n", expect = "OK" }
              ]
            }
            """.trimIndent()
        )
        try {
            assertEquals(0, runCli(arrayOf("test", config.path)))
        } finally {
            server.closePort()
        }
    }

    @Test
    fun gatewayForwardsBetweenTcpEndpoints() {
        val portA = freePort()
        val portB = freePort()
        val config = tempConfig(
            """
            gateway {
              endpoints = [
                { id = "a", name = "A", port = "tcp-listen://$portA" },
                { id = "b", name = "B", port = "tcp-listen://$portB" }
              ]
              routes = [ { name = "a-to-b", sources = ["a"], targets = ["b"] } ]
            }
            """.trimIndent()
        )

        val exit = AtomicInteger(-1)
        val worker = thread { exit.set(runCli(arrayOf("gateway", config.path, "--duration", "4"))) }
        val clientA = TcpTransport()
        val clientB = TcpTransport()
        val received = CopyOnWriteArrayList<String>()
        try {
            assertTrue(connect(clientA, portA), "client A could not connect")
            assertTrue(connect(clientB, portB), "client B could not connect")
            clientB.addDataListener { received += String(it) }
            // 等待网关侧完成 accept，再用重试写保证首帧不会因为"还没接上"被拒。
            assertTrue(clientA.write("PING".toByteArray()) > 0)
            assertTrue(waitUntil { received.isNotEmpty() }, "forwarded frame did not arrive")
            assertEquals("PING", received.first())
        } finally {
            clientA.closePort()
            clientB.closePort()
            worker.join(6_000)
        }
    }

    private fun connect(transport: TcpTransport, port: Int): Boolean = waitUntil {
        if (!transport.isOpen) transport.openPort("tcp://127.0.0.1:$port", 9600, 8, 0, 0, 0)
        // hasPeer 而不是写探测：探测字节会被转发出去污染数据流。
        transport.isOpen && transport.hasPeer
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun tempConfig(text: String): File = File.createTempFile("kserial-cli-", ".conf").apply {
        writeText(text)
        deleteOnExit()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
