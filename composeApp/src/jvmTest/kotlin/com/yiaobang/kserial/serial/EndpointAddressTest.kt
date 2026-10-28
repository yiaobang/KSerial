package com.yiaobang.kserial.serial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EndpointAddressTest {

    @Test
    fun plainStringStaysSerialPort() {
        assertEquals(EndpointAddress.Serial("COM3"), EndpointAddress.parse("COM3"))
        assertEquals(EndpointAddress.Serial("/dev/ttyUSB0"), EndpointAddress.parse("/dev/ttyUSB0"))
        assertTrue(!EndpointAddress.parse("COM3")!!.isNetwork)
    }

    @Test
    fun tcpUrlParsesIntoClient() {
        val address = EndpointAddress.parse("tcp://192.168.1.20:8080")
        assertEquals(EndpointAddress.TcpClient("192.168.1.20", 8080), address)
    }

    @Test
    fun listenFormsParseIntoServer() {
        assertEquals(EndpointAddress.TcpServer(9000), EndpointAddress.parse("tcp://:9000"))
        assertEquals(EndpointAddress.TcpServer(9000), EndpointAddress.parse("tcp-listen://9000"))
        assertEquals(EndpointAddress.TcpServer(9000), EndpointAddress.parse("tcp-server://9000"))
        // 只有端口没有冒号也按监听处理
        assertEquals(EndpointAddress.TcpServer(9000), EndpointAddress.parse("tcp://9000"))
    }

    @Test
    fun udpPeerAndListenerAreDistinguished() {
        assertEquals(EndpointAddress.UdpPeer("10.0.0.5", 5000), EndpointAddress.parse("udp://10.0.0.5:5000"))
        assertEquals(EndpointAddress.UdpListen(5000), EndpointAddress.parse("udp://:5000"))
        assertEquals(EndpointAddress.UdpListen(5000), EndpointAddress.parse("udp-listen://5000"))
    }

    @Test
    fun malformedNetworkAddressIsRejected() {
        assertNull(EndpointAddress.parse("tcp://host:"))
        assertNull(EndpointAddress.parse("tcp://host:70000"))
        assertNull(EndpointAddress.parse("tcp://host:abc"))
        assertNull(EndpointAddress.parse("   "))
    }

    @Test
    fun ipv6LiteralIsAccepted() {
        assertIs<EndpointAddress.TcpClient>(EndpointAddress.parse("tcp://[::1]:8080"))
    }

    @Test
    fun networkPrefixDetectionSkipsSerialEnumeration() {
        assertTrue(EndpointAddress.looksLikeNetwork("tcp://127.0.0.1:1"))
        assertTrue(!EndpointAddress.looksLikeNetwork("COM3"))
    }

    @Test
    fun serialPortCountsAsLostOnlyWhenMissingFromList() {
        assertTrue(EndpointAddress.lostFromPortList("COM3", listOf("COM1", "COM2")))
        assertFalse(EndpointAddress.lostFromPortList("COM3", listOf("COM3")))
        assertTrue(EndpointAddress.lostFromPortList("COM3", emptyList()))
    }

    /**
     * 回归：网络端点不在串口枚举里，如果按"端口消失"处理，
     * 打开 tcp:// 后端口列表一刷新就会把连接断开。
     */
    @Test
    fun networkEndpointsAreNeverTreatedAsLost() {
        listOf(
            "tcp://192.168.1.20:8080",
            "tcp://:9000",
            "tcp-listen://9000",
            "udp://192.168.1.20:9000",
            "udp-listen://9000"
        ).forEach { address ->
            assertFalse(
                EndpointAddress.lostFromPortList(address, emptyList()),
                "$address must never be treated as a lost port"
            )
        }
    }
}
