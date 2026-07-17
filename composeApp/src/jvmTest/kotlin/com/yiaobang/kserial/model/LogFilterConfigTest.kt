package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogFilterConfigTest {
    private val rxLog = LogEntry("12:00:00", LogType.RX, "Device ERROR")

    @Test
    fun onlyMatchesUsesCaseInsensitiveContentSearch() {
        val filter = LogFilterConfig().apply {
            onlyMatches = true
            query = "error"
        }

        assertTrue(filter.accepts(rxLog))
    }

    @Test
    fun typeFilterHasPriority() {
        val filter = LogFilterConfig().apply {
            onlyMatches = true
            query = "error"
            showRx = false
        }

        assertFalse(filter.accepts(rxLog))
    }
}
