package com.yiaobang.kserial.serial

import com.yiaobang.kserial.model.SerialLineSignals
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 传输层路由：按地址前缀把 openPort 分派到串口或网络实现。
 *
 * 上层（终端页、模拟器、桥接、录制）只持有 [SerialTransport]，因此
 * `tcp://192.168.1.20:8080` 与 `COM3` 在使用上完全等价；监听器在路由层统一保管，
 * 切换底层实现时不需要调用方重新注册。
 */
class TransportRouter : SerialTransport {

    private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

    private var delegate: SerialTransport? = null

    private val dataProxy: (ByteArray) -> Unit = { data ->
        dataListeners.forEach { listener -> runCatching { listener(data) } }
    }

    private val transportProxy: (SerialTransportEvent) -> Unit = { event ->
        transportListeners.forEach { listener -> runCatching { listener(event) } }
    }

    override val lastErrorMessage: String?
        get() = delegate?.lastErrorMessage

    override val isOpen: Boolean
        get() = delegate?.isOpen == true

    /** 当前实际使用的实现，便于 UI 显示"串口 / TCP / UDP"。 */
    val activeKind: String
        get() = when (delegate) {
            is TcpTransport -> "TCP"
            is UdpTransport -> "UDP"
            is SerialComm -> "SERIAL"
            else -> ""
        }

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
        val address = EndpointAddress.parse(portName)
            ?: return false.also { delegate = null }
        val next: SerialTransport = when (address) {
            is EndpointAddress.Serial -> SerialComm()
            is EndpointAddress.TcpClient, is EndpointAddress.TcpServer -> TcpTransport()
            is EndpointAddress.UdpPeer, is EndpointAddress.UdpListen -> UdpTransport()
        }
        closeDelegate()
        delegate = next
        next.addDataListener(dataProxy)
        next.addTransportListener(transportProxy)
        val opened = next.openPort(
            portName = portName,
            baudRate = baudRate,
            dataBits = dataBits,
            stopBitsIndex = stopBitsIndex,
            parityIndex = parityIndex,
            flowControlIndex = flowControlIndex
        )
        if (!opened) closeDelegate()
        return opened
    }

    override fun closePort() {
        closeDelegate()
    }

    override fun setDtr(enabled: Boolean): Boolean = delegate?.setDtr(enabled) ?: false

    override fun setRts(enabled: Boolean): Boolean = delegate?.setRts(enabled) ?: false

    override fun setBreak(enabled: Boolean): Boolean = delegate?.setBreak(enabled) ?: false

    override fun lineSignals(): SerialLineSignals = delegate?.lineSignals() ?: SerialLineSignals()

    override fun write(data: ByteArray): Int = delegate?.write(data) ?: -1

    private fun closeDelegate() {
        delegate?.let {
            runCatching { it.closePort() }
            runCatching { it.removeDataListener(dataProxy) }
            runCatching { it.removeTransportListener(transportProxy) }
        }
        delegate = null
    }
}
