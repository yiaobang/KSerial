package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 会话日志的非破坏性显示过滤条件；不会修改 ViewModel 中的原始日志。 */
class LogFilterConfig {
    var query by mutableStateOf("")
    var onlyMatches by mutableStateOf(false)
    var autoScroll by mutableStateOf(true)
    var showTx by mutableStateOf(true)
    var showRx by mutableStateOf(true)
    var showInfo by mutableStateOf(true)

    fun accepts(log: LogEntry): Boolean {
        val typeAccepted = when (log.type) {
            LogType.TX -> showTx
            LogType.RX -> showRx
            LogType.INFO -> showInfo
        }
        if (!typeAccepted) return false
        // query 始终可用于界面高亮，只有 onlyMatches 开启时才真正隐藏未命中项。
        if (!onlyMatches || query.isBlank()) return true
        return log.content.contains(query, ignoreCase = true) ||
            log.time.contains(query, ignoreCase = true)
    }

    fun reset() {
        query = ""
        onlyMatches = false
        showTx = true
        showRx = true
        showInfo = true
    }
}
