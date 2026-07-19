package com.yiaobang.kserial.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 串口通信核心类
 * 职责单一：仅负责管理物理串口连接和数据收发
 * 通过 dataFlow 和回调机制将数据分发给外部消费者（模拟器、持久化等）
 */
class SerialComm : SerialPortDataListener {
    /** 物理串口句柄 */
    private var serialPort: SerialPort? = null

    /** 基于 Flow 的数据流转发，用于 UI 层的实时刷新 */
    private val _dataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 100)
    val dataFlow = _dataFlow.asSharedFlow()

    /** 串口输出流 */
    private var outputStream: OutputStream? = null

    @Volatile
    var lastErrorMessage: String? = null
        private set

    /** 外部数据监听器列表（解耦模拟器、持久化等关注方） */
    private val dataListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()

    /** 注册数据到达监听器 */
    fun addDataListener(listener: (ByteArray) -> Unit) {
        dataListeners += listener
    }

    /** 移除数据到达监听器 */
    fun removeDataListener(listener: (ByteArray) -> Unit) {
        dataListeners -= listener
    }

    /** 清除所有数据到达监听器 */
    fun clearDataListeners() {
        dataListeners.clear()
    }

    /** 获取系统当前所有可用的串口名称 */
    fun getAvailablePorts(): List<String> = SerialPort.getCommPorts().map { it.systemPortName }

    /** 实现 jSerialComm 监听接口：只关注数据到达事件 */
    override fun getListeningEvents(): Int = SerialPort.LISTENING_EVENT_DATA_AVAILABLE

    /**
     * 串口底层数据到达的回调
     * 1. 读取数据内容
     * 2. 通知所有外部监听器
     * 3. 向 UI 层发射数据流
     */
    override fun serialEvent(event: SerialPortEvent) {
        if (event.eventType != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return
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
        _dataFlow.tryEmit(data)
    }

    /**
     * 打开物理串口并配置参数
     */
    @Synchronized
    fun openPort(
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
    fun closePort() {
        serialPort?.let {
            it.removeDataListener()
            it.closePort()
        }
        serialPort = null
        outputStream = null
    }

    /** 发送同步数据字节，返回发送字节数，失败返回 -1 */
    @Synchronized
    fun write(data: ByteArray): Int {
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
}
