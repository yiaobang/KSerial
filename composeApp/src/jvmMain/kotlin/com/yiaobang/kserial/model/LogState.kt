package com.yiaobang.kserial.model

/**
 * 日志类型枚举 (发送/接收/系统)
 */
enum class LogType { 
    TX, // 发送数据
    RX, // 接收数据
    INFO // 系统提示信息
}

/**
 * 每一行日志的数据结构
 */
data class LogEntry(
    val time: String,      // 时间戳 (HH:mm:ss)
    val type: LogType,      // 类型
    val content: String,    // 数据内容 (HEX 或 字符串)
    val id: Long = 0L       // 页面内稳定标识，供 Compose 列表复用
)
