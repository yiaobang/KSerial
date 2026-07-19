package com.yiaobang.kserial.model

import com.yiaobang.kserial.i18n.Language

data class PersistedSettings(
    val schemaVersion: Int = 1,
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

data class PersistedPreferences(
    val showRealTime: Boolean = true,
    val hexDisplay: Boolean = false,
    val hexSend: Boolean = false,
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
