package com.yiaobang.kserial.serial

import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 用本机 loopback 验证网络端点真的能收发，避免只测地址解析不测通路。 */
class NetworkTransportTest {

    @Test
    fun tcpServerAndClientExchangeDataBothWays() {
        val port = ServerSocket(0).use { it.localPort }
        val server = TcpTransport()
        val client = TcpTransport()
        val serverReceived = CopyOnWriteArrayList<String>()
        val clientReceived = CopyOnWriteArrayList<String>()
        server.addDataListener { serverReceived += String(it) }
        client.addDataListener { clientReceived += String(it) }
        try {
            assertTrue(server.openPort("tcp-listen://$port", 9600, 8, 0, 0, 0))
            assertTrue(client.openPort("tcp://127.0.0.1:$port", 9600, 8, 0, 0, 0))
            // 连接在后台建立，等到服务端能写成功即表示对端已接入。
            assertTrue(waitUntil { server.write("ping".toByteArray()) > 0 })
            assertTrue(waitUntil { clientReceived.size == 1 })
            assertEquals("ping", clientReceived.single())

            assertTrue(waitUntil { client.write("pong".toByteArray()) > 0 })
            assertTrue(waitUntil { serverReceived.size == 1 })
            assertEquals("pong", serverReceived.single())
        } finally {
            client.closePort()
            server.closePort()
        }
    }

    @Test
    fun writeWithoutPeerFailsInsteadOfSilentlyDropping() {
        val transport = TcpTransport()
        try {
            // 端口留空会导致连接失败，但 openPort 本身是异步的；写操作必须在无对端时返回 -1。
            assertTrue(transport.openPort("tcp://127.0.0.1:1", 9600, 8, 0, 0, 0))
            assertEquals(-1, transport.write("x".toByteArray()))
        } finally {
            transport.closePort()
        }
    }

    @Test
    fun udpListenerLearnsPeerAndReplies() {
        val port = DatagramSocket(0).use { it.localPort }
        val listener = UdpTransport()
        val peer = UdpTransport()
        val listenerReceived = CopyOnWriteArrayList<String>()
        val peerReceived = CopyOnWriteArrayList<String>()
        listener.addDataListener { listenerReceived += String(it) }
        peer.addDataListener { peerReceived += String(it) }
        try {
            assertTrue(listener.openPort("udp-listen://$port", 9600, 8, 0, 0, 0))
            assertTrue(peer.openPort("udp://127.0.0.1:$port", 9600, 8, 0, 0, 0))
            assertTrue(peer.write("hello".toByteArray()) > 0)
            assertTrue(waitUntil { listenerReceived.size == 1 })
            assertEquals("hello", listenerReceived.single())

            // 监听端从首个报文里学到对端地址，之后才允许回发。
            assertTrue(waitUntil { listener.write("ack".toByteArray()) > 0 })
            assertTrue(waitUntil { peerReceived.size == 1 })
            assertEquals("ack", peerReceived.single())
        } finally {
            peer.closePort()
            listener.closePort()
        }
    }

    @Test
    fun transportRouterDispatchesByAddressPrefix() {
        val router = TransportRouter()
        val received = CopyOnWriteArrayList<String>()
        router.addDataListener { received += String(it) }
        val port = ServerSocket(0).use { it.localPort }
        val server = TcpTransport()
        try {
            assertTrue(server.openPort("tcp-listen://$port", 9600, 8, 0, 0, 0))
            assertTrue(router.openPort("tcp://127.0.0.1:$port", 9600, 8, 0, 0, 0))
            assertTrue(waitUntil { server.write("via-router".toByteArray()) > 0 })
            assertTrue(waitUntil { received.size == 1 })
            assertEquals("via-router", received.single())
            assertEquals("TCP", router.activeKind)
        } finally {
            router.closePort()
            server.closePort()
        }
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
