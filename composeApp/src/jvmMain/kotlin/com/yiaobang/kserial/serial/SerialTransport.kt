package com.yiaobang.kserial.serial

import com.yiaobang.kserial.model.SerialLineSignals

/**
 * 串口传输边界。页面和协议功能依赖此接口，不依赖 jSerialComm 的具体实现，
 * 因而可以在协议测试中替换为内存传输或录制回放传输。
 */
interface SerialTransport {
    val lastErrorMessage: String?
    val isOpen: Boolean

    fun addDataListener(listener: (ByteArray) -> Unit)
    fun removeDataListener(listener: (ByteArray) -> Unit)
    fun clearDataListeners()

    fun addTransportListener(listener: (SerialTransportEvent) -> Unit)
    fun removeTransportListener(listener: (SerialTransportEvent) -> Unit)
    fun clearTransportListeners()

    fun openPort(
        portName: String,
        baudRate: Int,
        dataBits: Int,
        stopBitsIndex: Int,
        parityIndex: Int,
        flowControlIndex: Int
    ): Boolean

    fun closePort()
    fun setDtr(enabled: Boolean): Boolean
    fun setRts(enabled: Boolean): Boolean
    fun setBreak(enabled: Boolean): Boolean
    fun lineSignals(): SerialLineSignals
    fun write(data: ByteArray): Int
}
