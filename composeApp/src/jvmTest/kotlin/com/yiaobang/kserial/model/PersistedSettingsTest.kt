package com.yiaobang.kserial.model

import com.yiaobang.kserial.i18n.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistedSettingsTest {
    @Test
    fun applyingPersistedStateClampsSerialValuesAndResetsTransientLoopState() {
        val serial = SerialPortConfig().apply {
            applyPersisted(
                PersistedSerialSettings(
                    selectedPort = "COM9",
                    baudRate = -1,
                    dataBits = 99,
                    stopBitsIndex = 9,
                    parityIndex = -2,
                    flowControlIndex = 7
                )
            )
        }
        assertEquals(PersistedSerialSettings("COM9", 9600, 8, 2, 0, 3), serial.toPersisted())

        val preferences = AppPreferences().apply {
            loopSendEnabled = true
            loopIntervalText = "15"
            applyPersisted(
                PersistedAppearance(darkTheme = true, alwaysOnTop = true, language = "ja"),
                PersistedPreferences(
                    showRealTime = false,
                    hexDisplay = true,
                    hexSend = true,
                    saveSend = true,
                    saveReceive = true,
                    quickCommandsText = "PING",
                    highlightEnabled = true,
                    highlightIgnoreCase = false,
                    highlightKeywords = "ERROR",
                    logQuery = "WARN",
                    logOnlyMatches = true,
                    logAutoScroll = false,
                    logShowTx = false,
                    logShowRx = false,
                    logShowInfo = false
                )
            )
        }
        assertTrue(preferences.isDark)
        assertTrue(preferences.isAlwaysOnTop)
        assertEquals(Language.JA, preferences.currentLanguage)
        assertFalse(preferences.loopSendEnabled)
        assertEquals("1000", preferences.loopIntervalText)
        assertEquals("ERROR", preferences.highlight.keywordsText)
        assertEquals("WARN", preferences.logFilter.query)
        assertEquals("PING", preferences.toPersistedPreferences().quickCommandsText)
    }

    @Test
    fun unknownPersistedLanguageFallsBackToCurrentLocaleLanguage() {
        val preferences = AppPreferences()
        preferences.applyPersisted(PersistedAppearance(language = "unknown"), PersistedPreferences())
        assertEquals(Language.fromLocale(), preferences.currentLanguage)
    }
}
