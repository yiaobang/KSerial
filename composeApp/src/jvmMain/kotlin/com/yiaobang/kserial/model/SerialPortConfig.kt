package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 串口连接参数状态持有类
 * 使用 Compose State Holder 模式，将连接配置从 ViewModel 中解耦
 */
class SerialPortConfig {
    /** 当前选中的串口名称 */
    var selectedPort by mutableStateOf("")

    /** 波特率 */
    var baudRate by mutableStateOf(9600)

    /** 数据位 */
    var dataBits by mutableStateOf(8)

    /** 停止位索引 (0=1, 1=1.5, 2=2) */
    var stopBitsIndex by mutableStateOf(0)

    /** 校验位索引 (0=None, 1=Odd, 2=Even, 3=Mark, 4=Space) */
    var parityIndex by mutableStateOf(0)

    /** 流控索引 (0=None, 1=RTS/CTS, 2=DSR/DTR, 3=XON/XOFF) */
    var flowControlIndex by mutableStateOf(0)

    companion object {
        val BAUD_RATES = listOf(9600, 19200, 38400, 115200, 128000, 230400, 256000, 460800, 921600, 1382400)
        val DATA_BITS_LIST = listOf(8, 7, 6, 5)
    }
}
