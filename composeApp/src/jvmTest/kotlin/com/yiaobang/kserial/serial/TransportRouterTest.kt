package com.yiaobang.kserial.serial

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 传输路由的补充单测：这里只覆盖 NetworkTransportTest 未涉及的行为——
 * 无委托时的默认值、非法地址拒绝、UDP 分派，以及切换底层实现后监听器仍然生效。
 */
class TransportRouterTest {

    @Test
    fun defaultsBeforeAnyOpen() {
        val router = TransportRouter()
        assertFalse(router.isOpen)
        assertEquals("", router.activeKind)
        assertEquals(-1, router.write("x".encodeToByteArray()))
        assertEquals(null, router.lastErrorMessage)
    }

    @Test
    fun invalidAddressIsRejectedWithoutSideEffects() {
        val router = TransportRouter()
        assertFalse(router.openPort("this-is-not-an-address", 9600, 8, 0, 0, 0))
        assertEquals("", router.activeKind)
        assertFalse(router.isOpen)
    }

    @Test
    fun udpDispatchReportsUdpKindAndDeliversDatagrams() {
        val router = TransportRouter()
        val received = CopyOnWriteArrayList<String>()
        router.addDataListener { received += String(it) }
        val port = ServerSocket(0).use { it.localPort }
        try {
            assertTrue(router.openPort("udp-listen://$port", 9600, 8, 0, 0, 0))
            assertEquals("UDP", router.activeKind)

            DatagramSocket().use { sender ->
                val packet = DatagramPacket(
                    "datagram".encodeToByteArray(),
                    8,
                    InetAddress.getByName("127.0.0.1"),
                    port
                )
                sender.send(packet)
            }
            assertTrue(waitUntil { received.size == 1 }, "UDP datagram should reach the router listener")
            assertEquals("datagram", received.single())
        } finally {
            router.closePort()
        }
        assertEquals("", router.activeKind)
        assertFalse(router.isOpen)
    }

    @Test
    fun listenersSurviveSwitchingUnderlyingImplementations() {
        val router = TransportRouter()
        val received = CopyOnWriteArrayList<String>()
        router.addDataListener { received += String(it) }

        val udpPort = ServerSocket(0).use { it.localPort }
        val tcpPort = ServerSocket(0).use { it.localPort }
        try {
            assertTrue(router.openPort("udp-listen://$udpPort", 9600, 8, 0, 0, 0))
            assertEquals("UDP", router.activeKind)
            DatagramSocket().use { sender ->
                sender.send(
                    DatagramPacket("one".encodeToByteArray(), 3, InetAddress.getByName("127.0.0.1"), udpPort)
                )
            }
            assertTrue(waitUntil { received.size == 1 })

            // 直接切换到 TCP 服务端：监听器注册在路由层，不应要求调用方重新注册。
            assertTrue(router.openPort("tcp-listen://$tcpPort", 9600, 8, 0, 0, 0))
            assertEquals("TCP", router.activeKind)
            // 服务端绑定是后台完成的，连接需要稍作重试。
            val client = connectWithRetry(tcpPort)
            client.use { c ->
                c.getOutputStream().apply {
                    write("two".encodeToByteArray())
                    flush()
                }
                assertTrue(waitUntil { received.size == 2 }, "listener must keep working after the switch")
            }
            assertEquals(listOf("one", "two"), received)
        } finally {
            router.closePort()
        }
        assertFalse(router.isOpen, "reopen must have replaced and closed the previous delegate")
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    /** TcpTransport 服务端是后台绑定，客户端连接需要重试到绑定完成。 */
    private fun connectWithRetry(port: Int, timeoutMs: Long = 3_000): Socket {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                return Socket("127.0.0.1", port)
            } catch (e: java.io.IOException) {
                lastError = e
                Thread.sleep(20)
            }
        }
        throw lastError ?: IllegalStateException("Unable to connect to 127.0.0.1:$port")
    }
}
