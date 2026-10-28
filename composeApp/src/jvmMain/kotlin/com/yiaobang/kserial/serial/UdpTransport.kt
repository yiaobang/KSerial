package com.yiaobang.kserial.serial

import com.yiaobang.kserial.model.SerialLineSignals
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * UDP 端点：面向"串口转 UDP"模块和广播式传感器。
 *
 * 数据报天然带边界，这里按报文逐个回调，上层拿到的每个 ByteArray 就是一次 recvfrom；
 * 监听模式下用首个报文的对端地址作为回发目标（UDP 无连接，只能这样"记住"对端）。
 */
class UdpTransport : SerialTransport {

    private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

    private val peerLock = Any()
    private var socket: DatagramSocket? = null
    private var fixedPeer: InetSocketAddress? = null
    private var learnedPeer: InetSocketAddress? = null

    @Volatile
    private var running = false

    @Volatile
    private var closing = false

    override var lastErrorMessage: String? = null
        private set

    override val isOpen: Boolean
        get() = running && socket?.isClosed == false

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
        closePort()
        val address = EndpointAddress.parse(portName)
        if (address !is EndpointAddress.UdpPeer && address !is EndpointAddress.UdpListen) {
            lastErrorMessage = "Not a UDP endpoint: $portName"
            return false
        }
        val bindPort = when (address) {
            is EndpointAddress.UdpPeer -> 0
            is EndpointAddress.UdpListen -> address.port
            else -> return false
        }
        return try {
            val bound = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(bindPort))
            }
            socket = bound
            fixedPeer = (address as? EndpointAddress.UdpPeer)?.let {
                InetSocketAddress(InetAddress.getByName(it.host), it.port)
            }
            learnedPeer = null
            closing = false
            running = true
            startReceiveLoop(bound)
            true
        } catch (error: Exception) {
            lastErrorMessage = "${portName}: ${error.message ?: error::class.simpleName}"
            running = false
            false
        }
    }

    override fun closePort() {
        closing = true
        running = false
        runCatching { socket?.close() }
        socket = null
        fixedPeer = null
        learnedPeer = null
    }

    override fun setDtr(enabled: Boolean): Boolean = false

    override fun setRts(enabled: Boolean): Boolean = false

    override fun setBreak(enabled: Boolean): Boolean = false

    override fun lineSignals(): SerialLineSignals = SerialLineSignals()

    override fun write(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val target = synchronized(peerLock) { fixedPeer ?: learnedPeer }
        val datagram = socket
        if (target == null || datagram == null) {
            notifyTransport(SerialTransportEvent.Error("no peer address yet, data dropped"))
            return -1
        }
        return try {
            datagram.send(DatagramPacket(data, data.size, target))
            data.size
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error::class.simpleName
            -1
        }
    }

    private fun startReceiveLoop(datagram: DatagramSocket) {
        thread(isDaemon = true, name = "kserial-udp") {
            val buffer = ByteArray(64 * 1024)
            try {
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    datagram.receive(packet)
                    if (packet.length <= 0) continue
                    if (fixedPeer == null) {
                        synchronized(peerLock) { learnedPeer = packet.socketAddress as? InetSocketAddress }
                    }
                    val chunk = packet.data.copyOf(packet.length)
                    dataListeners.forEach { listener -> runCatching { listener(chunk) } }
                }
            } catch (error: Exception) {
                if (!closing) {
                    lastErrorMessage = error.message ?: error::class.simpleName
                    running = false
                    notifyTransport(SerialTransportEvent.Disconnected)
                }
            }
        }
    }

    private fun notifyTransport(event: SerialTransportEvent) {
        transportListeners.forEach { listener -> runCatching { listener(event) } }
    }
}
