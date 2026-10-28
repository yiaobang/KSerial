package com.yiaobang.kserial.serial

import com.yiaobang.kserial.model.SerialLineSignals
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * TCP 端点：把"串口"扩展成网络通道，让桥接/模拟器/终端可以对接串口服务器
 * （ser2net、ESP-Link、USR-TCP232 等）。
 *
 * 客户端模式连接远端；服务端模式先绑定端口再等待对端接入。连接动作放在后台线程，
 * 避免在 UI 线程阻塞；连接失败会以 Disconnected 事件上报，由上层页面关闭端点并提示。
 */
class TcpTransport : SerialTransport {

    private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

    private val peerLock = Any()
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    @Volatile
    private var listening = false

    @Volatile
    private var closing = false

    private var readerThread: Thread? = null

    @Volatile
    private var lastWarningAt = 0L

    override var lastErrorMessage: String? = null
        private set

    override val isOpen: Boolean
        get() = running

    /** 是否已有对端：服务端监听成功但没人接入时为 false，UI 和测试据此区分"监听中/已连通"。 */
    val hasPeer: Boolean
        get() = synchronized(peerLock) { socket?.let { !it.isClosed } == true }

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
        if (address !is EndpointAddress.TcpClient && address !is EndpointAddress.TcpServer) {
            lastErrorMessage = "Not a TCP endpoint: $portName"
            return false
        }
        lastErrorMessage = null
        closing = false
        running = true
        when (address) {
            is EndpointAddress.TcpClient -> startConnect(address)
            is EndpointAddress.TcpServer -> startListen(address)
            else -> return false
        }
        return true
    }

    override fun closePort() {
        closing = true
        running = false
        listening = false
        synchronized(peerLock) {
            runCatching { socket?.close() }
            socket = null
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        readerThread = null
    }

    override fun setDtr(enabled: Boolean): Boolean = false

    override fun setRts(enabled: Boolean): Boolean = false

    override fun setBreak(enabled: Boolean): Boolean = false

    override fun lineSignals(): SerialLineSignals = SerialLineSignals()

    override fun write(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val peer = awaitPeer(WRITE_WAIT_MS)
        if (peer == null) {
            warnNoPeer()
            return -1
        }
        return try {
            peer.getOutputStream().apply {
                write(data)
                flush()
            }
            data.size
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error::class.simpleName
            dropPeer(peer)
            -1
        }
    }

    private fun startConnect(address: EndpointAddress.TcpClient) {
        thread(isDaemon = true, name = "kserial-tcp-connect") {
            try {
                val peer = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(address.host, address.port), CONNECT_TIMEOUT_MS)
                }
                if (!running) {
                    runCatching { peer.close() }
                    return@thread
                }
                synchronized(peerLock) { socket = peer }
                readLoop(peer, serverSide = false)
            } catch (error: Exception) {
                fail("${address.host}:${address.port}", error)
            }
        }
    }

    private fun startListen(address: EndpointAddress.TcpServer) {
        thread(isDaemon = true, name = "kserial-tcp-accept") {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(address.port))
                serverSocket = server
                listening = true
                while (running) {
                    val peer = server.accept()
                    peer.tcpNoDelay = true
                    synchronized(peerLock) {
                        runCatching { socket?.close() }
                        socket = peer
                    }
                    // 每个接入的连接单独读；服务端继续 accept，掉线后可以重新接入。
                    thread(isDaemon = true, name = "kserial-tcp-read") {
                        readLoop(peer, serverSide = true)
                    }
                }
            } catch (error: Exception) {
                if (!closing) fail("port ${address.port}", error)
            }
        }
    }

    private fun readLoop(peer: Socket, serverSide: Boolean) {
        val buffer = ByteArray(8 * 1024)
        try {
            val input = peer.getInputStream()
            while (running) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                val chunk = buffer.copyOf(read)
                dataListeners.forEach { listener -> runCatching { listener(chunk) } }
            }
            if (running) {
                // 对端主动断开：客户端模式视为连接结束，服务端模式只丢开这条连接继续监听。
                dropPeer(peer)
                if (serverSide) {
                    notifyTransport(SerialTransportEvent.Error("peer disconnected"))
                } else {
                    lastErrorMessage = "Connection closed by peer"
                    running = false
                    notifyTransport(SerialTransportEvent.Disconnected)
                }
            }
        } catch (error: Exception) {
            if (!closing && running) {
                dropPeer(peer)
                if (serverSide) {
                    notifyTransport(SerialTransportEvent.Error(error.message ?: "connection error"))
                } else {
                    lastErrorMessage = error.message ?: error::class.simpleName
                    running = false
                    notifyTransport(SerialTransportEvent.Disconnected)
                }
            }
        }
    }

    private fun dropPeer(peer: Socket) {
        synchronized(peerLock) {
            if (socket === peer) socket = null
            runCatching { peer.close() }
        }
    }

    /** 等待对端就绪；未接入时短暂等待，超时后由调用方返回失败（不静默吞数据）。 */
    private fun awaitPeer(timeoutMs: Long): Socket? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            synchronized(peerLock) {
                val peer = socket
                if (peer != null && !peer.isClosed) return peer
            }
            if (!running || System.currentTimeMillis() >= deadline) return null
            Thread.sleep(5)
        }
    }

    private fun warnNoPeer() {
        // 未接入对端时写数据必然丢弃；限流上报，避免高频桥接把日志刷爆。
        val now = System.currentTimeMillis()
        if (now - lastWarningAt < NO_PEER_WARNING_INTERVAL_MS) return
        lastWarningAt = now
        notifyTransport(SerialTransportEvent.Error("no peer connected, data dropped"))
    }

    private fun fail(target: String, error: Exception) {
        if (closing) return
        lastErrorMessage = "$target: ${error.message ?: error::class.simpleName}"
        running = false
        notifyTransport(SerialTransportEvent.Disconnected)
    }

    private fun notifyTransport(event: SerialTransportEvent) {
        transportListeners.forEach { listener -> runCatching { listener(event) } }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val WRITE_WAIT_MS = 2_000L
        const val NO_PEER_WARNING_INTERVAL_MS = 1_000L
    }
}
