package com.yiaobang.kserial.model

import com.yiaobang.kserial.i18n.Language

/** settings.conf 的稳定磁盘模型；与 Compose 可变状态分离，便于版本迁移和测试。 */
data class PersistedSettings(
    val schemaVersion: Int = 2,
    val appearance: PersistedAppearance = PersistedAppearance(),
    val serial: PersistedSerialSettings = PersistedSerialSettings(),
    val preferences: PersistedPreferences = PersistedPreferences(),
    val sendHistory: List<String> = emptyList()
)

data class PersistedAppearance(
    val darkTheme: Boolean = false,
    val alwaysOnTop: Boolean = false,
    val language: String = ""
)

data class PersistedSerialSettings(
    val selectedPort: String = "",
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBitsIndex: Int = 0,
    val parityIndex: Int = 0,
    val flowControlIndex: Int = 0
)

/**
 * 可以跨会话恢复的交互偏好。发送输入、循环发送开关和循环间隔有意不在此模型中，
 * 防止重启后意外继续发送上一次的数据。
 */
data class PersistedPreferences(
    val showRealTime: Boolean = true,
    val hexDisplay: Boolean = false,
    val hexSend: Boolean = false,
    val receiveDisplayMode: String = ReceiveDisplayMode.TIME_WINDOW.name,
    val receiveWindowMs: String = "25",
    val receiveDelimiter: String = "\\r\\n",
    val receiveFrameSize: String = "8",
    val sendLineEnding: String = SendLineEnding.NONE.name,
    val enterToSend: Boolean = false,
    val clearAfterSend: Boolean = false,
    val autoReconnect: Boolean = false,
    val reconnectIntervalMs: String = "2000",
    val saveSend: Boolean = false,
    val saveReceive: Boolean = false,
    val quickCommandsText: String = "AT\\r\\n\nAT+VERSION\\r\\n",
    val highlightEnabled: Boolean = false,
    val highlightIgnoreCase: Boolean = true,
    val highlightKeywords: String = "",
    val logQuery: String = "",
    val logOnlyMatches: Boolean = false,
    val logAutoScroll: Boolean = true,
    val logShowTx: Boolean = true,
    val logShowRx: Boolean = true,
    val logShowInfo: Boolean = true
)

fun AppPreferences.applyPersisted(
    appearance: PersistedAppearance,
    settings: PersistedPreferences
) {
    isDark = appearance.darkTheme
    isAlwaysOnTop = appearance.alwaysOnTop
    currentLanguage = Language.entries.firstOrNull { it.languageTag == appearance.language }
        ?: Language.fromLocale()
    showRealTime = settings.showRealTime
    hexDisplay = settings.hexDisplay
    hexSend = settings.hexSend
    receiveDisplayMode = ReceiveDisplayMode.entries.firstOrNull { it.name == settings.receiveDisplayMode }
        ?: ReceiveDisplayMode.TIME_WINDOW
    receiveWindowMsText = settings.receiveWindowMs
    receiveDelimiterText = settings.receiveDelimiter
    receiveFrameSizeText = settings.receiveFrameSize
    sendLineEnding = SendLineEnding.entries.firstOrNull { it.name == settings.sendLineEnding }
        ?: SendLineEnding.NONE
    enterToSend = settings.enterToSend
    clearAfterSend = settings.clearAfterSend
    autoReconnect = settings.autoReconnect
    reconnectIntervalText = settings.reconnectIntervalMs
    // 循环发送属于有副作用的临时状态，每次启动必须回到安全默认值。
    loopSendEnabled = false
    loopIntervalText = "1000"
    saveSend = settings.saveSend
    saveReceive = settings.saveReceive
    quickCommandsText = settings.quickCommandsText
    highlight.enabled = settings.highlightEnabled
    highlight.ignoreCase = settings.highlightIgnoreCase
    highlight.keywordsText = settings.highlightKeywords
    logFilter.query = settings.logQuery
    logFilter.onlyMatches = settings.logOnlyMatches
    logFilter.autoScroll = settings.logAutoScroll
    logFilter.showTx = settings.logShowTx
    logFilter.showRx = settings.logShowRx
    logFilter.showInfo = settings.logShowInfo
}

fun SerialPortConfig.applyPersisted(settings: PersistedSerialSettings) {
    // 对人工编辑或旧版本留下的越界值做最后一道收敛，避免 UI 下标访问异常。
    selectedPort = settings.selectedPort
    baudRate = settings.baudRate.takeIf { it > 0 } ?: 9600
    dataBits = settings.dataBits.coerceIn(5, 8)
    stopBitsIndex = settings.stopBitsIndex.coerceIn(0, 2)
    parityIndex = settings.parityIndex.coerceIn(0, 4)
    flowControlIndex = settings.flowControlIndex.coerceIn(0, 3)
}

fun AppPreferences.toPersistedAppearance(): PersistedAppearance = PersistedAppearance(
    darkTheme = isDark,
    alwaysOnTop = isAlwaysOnTop,
    language = currentLanguage.languageTag
)

fun AppPreferences.toPersistedPreferences(): PersistedPreferences = PersistedPreferences(
    showRealTime = showRealTime,
    hexDisplay = hexDisplay,
    hexSend = hexSend,
    receiveDisplayMode = receiveDisplayMode.name,
    receiveWindowMs = receiveWindowMsText,
    receiveDelimiter = receiveDelimiterText,
    receiveFrameSize = receiveFrameSizeText,
    sendLineEnding = sendLineEnding.name,
    enterToSend = enterToSend,
    clearAfterSend = clearAfterSend,
    autoReconnect = autoReconnect,
    reconnectIntervalMs = reconnectIntervalText,
    saveSend = saveSend,
    saveReceive = saveReceive,
    quickCommandsText = quickCommandsText,
    highlightEnabled = highlight.enabled,
    highlightIgnoreCase = highlight.ignoreCase,
    highlightKeywords = highlight.keywordsText,
    logQuery = logFilter.query,
    logOnlyMatches = logFilter.onlyMatches,
    logAutoScroll = logFilter.autoScroll,
    logShowTx = logFilter.showTx,
    logShowRx = logFilter.showRx,
    logShowInfo = logFilter.showInfo
)

fun SerialPortConfig.toPersisted(): PersistedSerialSettings = PersistedSerialSettings(
    selectedPort = selectedPort,
    baudRate = baudRate,
    dataBits = dataBits,
    stopBitsIndex = stopBitsIndex,
    parityIndex = parityIndex,
    flowControlIndex = flowControlIndex
)
