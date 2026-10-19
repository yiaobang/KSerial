package com.yiaobang.kserial.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import com.yiaobang.kserial.model.SerialLineSignals
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 串口通信核心类
 * 职责单一：仅负责管理物理串口连接和数据收发
 * 通过线程安全回调把数据分发给上层有序队列、模拟器和持久化记录器。
 */
class SerialComm : SerialTransport, SerialPortDataListener {
    /** 物理串口句柄 */
    private var serialPort: SerialPort? = null

    /** 串口输出流 */
    private var outputStream: OutputStream? = null

    @Volatile
    override var lastErrorMessage: String? = null
        private set

    /** 外部数据监听器列表（解耦模拟器、持久化等关注方） */
    private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()

    /** 断开和线路错误监听器。 */
    private val transportListeners = CopyOnWriteArrayList<(SerialTransportEvent) -> Unit>()

    /** 注册数据到达监听器 */
    override fun addDataListener(listener: (ByteArray) -> Unit) {
        dataListeners += listener
    }

    /** 移除数据到达监听器 */
    override fun removeDataListener(listener: (ByteArray) -> Unit) {
        dataListeners -= listener
    }

    /** 清除所有数据到达监听器 */
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

    /** 获取系统当前所有可用的串口名称 */
    fun getAvailablePorts(): List<String> = SerialPort.getCommPorts().map { it.systemPortName }

    /** 同时监听数据、物理断开和常见线路错误。 */
    override fun getListeningEvents(): Int =
        SerialPort.LISTENING_EVENT_DATA_AVAILABLE or
            SerialPort.LISTENING_EVENT_PORT_DISCONNECTED or
            SerialPort.LISTENING_EVENT_BREAK_INTERRUPT or
            SerialPort.LISTENING_EVENT_FRAMING_ERROR or
            SerialPort.LISTENING_EVENT_FIRMWARE_OVERRUN_ERROR or
            SerialPort.LISTENING_EVENT_SOFTWARE_OVERRUN_ERROR or
            SerialPort.LISTENING_EVENT_PARITY_ERROR

    /**
     * 串口底层数据到达的回调
     * 1. 读取数据内容
     * 2. 通知所有外部监听器
     * 3. 向 UI 层发射数据流
     */
    override fun serialEvent(event: SerialPortEvent) {
        if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            notifyTransport(SerialTransportEvent.Disconnected)
            return
        }
        if (event.eventType != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            notifyTransport(SerialTransportEvent.Error(eventName(event.eventType)))
            return
        }
        val data = runCatching {
            val port = event.serialPort
            val available = port.bytesAvailable()
            if (available <= 0) return
            val buffer = ByteArray(available)
            val len = port.readBytes(buffer, buffer.size)
            if (len <= 0) return
            buffer.copyOfRange(0, len)
        }.getOrNull() ?: return

        dataListeners.forEach { listener -> runCatching { listener(data) } }
    }

    /**
     * 打开物理串口并配置参数
     */
    @Synchronized
    override fun openPort(
        portName: String,
        baudRate: Int,
        dataBits: Int,
        stopBitsIndex: Int,
        parityIndex: Int,
        flowControlIndex: Int
    ): Boolean {
        // 先清理旧连接，确保逻辑幂等
        closePort()

        lastErrorMessage = null
        return try {
            val port = SerialPort.getCommPort(portName)
            serialPort = port
            val stopBits = when (stopBitsIndex) {
                1 -> SerialPort.ONE_POINT_FIVE_STOP_BITS
                2 -> SerialPort.TWO_STOP_BITS
                else -> SerialPort.ONE_STOP_BIT
            }

            val parity = when (parityIndex) {
                1 -> SerialPort.ODD_PARITY
                2 -> SerialPort.EVEN_PARITY
                3 -> SerialPort.MARK_PARITY
                4 -> SerialPort.SPACE_PARITY
                else -> SerialPort.NO_PARITY
            }

            val flowControl = when (flowControlIndex) {
                1 -> SerialPort.FLOW_CONTROL_RTS_ENABLED or SerialPort.FLOW_CONTROL_CTS_ENABLED
                2 -> SerialPort.FLOW_CONTROL_DSR_ENABLED or SerialPort.FLOW_CONTROL_DTR_ENABLED
                3 -> SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED
                else -> SerialPort.FLOW_CONTROL_DISABLED
            }

            port.setComPortParameters(baudRate, dataBits, stopBits, parity)
            port.setFlowControl(flowControl)
            // 配置写超时（1000ms），防止硬件流控开启且对端未拉高 CTS/DSR 时引发驱动层永久阻塞死锁
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 1000)
            if (!port.openPort()) {
                lastErrorMessage = port.descriptivePortName.takeIf { it.isNotBlank() }?.let { "Unable to open $it" }
                    ?: "Unable to open $portName"
                closePort()
                false
            } else {
                port.addDataListener(this)
                outputStream = port.outputStream
                true
            }
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error::class.simpleName
            closePort()
            false
        }
    }

    /** 释放串口资源 */
    @Synchronized
    override fun closePort() {
        serialPort?.let {
            it.removeDataListener()
            it.closePort()
        }
        serialPort = null
        outputStream = null
    }

    override val isOpen: Boolean
        @Synchronized get() = serialPort?.isOpen == true

    @Synchronized
    override fun setDtr(enabled: Boolean): Boolean = serialPort?.let { if (enabled) it.setDTR() else it.clearDTR() } ?: false

    @Synchronized
    override fun setRts(enabled: Boolean): Boolean = serialPort?.let { if (enabled) it.setRTS() else it.clearRTS() } ?: false

    @Synchronized
    override fun setBreak(enabled: Boolean): Boolean = serialPort?.let { if (enabled) it.setBreak() else it.clearBreak() } ?: false

    @Synchronized
    override fun lineSignals(): SerialLineSignals = serialPort?.takeIf { it.isOpen }?.let { port ->
        SerialLineSignals(
            cts = runCatching(port::getCTS).getOrDefault(false),
            dsr = runCatching(port::getDSR).getOrDefault(false),
            dcd = runCatching(port::getDCD).getOrDefault(false),
            ri = runCatching(port::getRI).getOrDefault(false)
        )
    } ?: SerialLineSignals()

    /** 发送同步数据字节，返回发送字节数，失败返回 -1 */
    @Synchronized
    override fun write(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val stream = outputStream ?: return -1
        return try {
            stream.write(data)
            stream.flush()
            data.size
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error::class.simpleName
            -1
        }
    }

    private fun notifyTransport(event: SerialTransportEvent) {
        transportListeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun eventName(type: Int): String = when (type) {
        SerialPort.LISTENING_EVENT_BREAK_INTERRUPT -> "break interrupt"
        SerialPort.LISTENING_EVENT_FRAMING_ERROR -> "framing error"
        SerialPort.LISTENING_EVENT_FIRMWARE_OVERRUN_ERROR -> "firmware overrun"
        SerialPort.LISTENING_EVENT_SOFTWARE_OVERRUN_ERROR -> "software overrun"
        SerialPort.LISTENING_EVENT_PARITY_ERROR -> "parity error"
        else -> "serial error $type"
    }
}
