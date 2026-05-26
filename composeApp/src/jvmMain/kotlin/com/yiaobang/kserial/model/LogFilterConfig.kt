package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
