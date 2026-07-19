package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppPreferencesTest {
    @Test
    fun loopIntervalAcceptsConfiguredRange() {
        val preferences = AppPreferences().apply { loopIntervalText = "250" }

        assertTrue(preferences.isLoopIntervalValid)
        assertEquals(250L, preferences.loopIntervalOrNull)
        assertEquals(250L, preferences.loopInterval)
    }

    @Test
    fun loopIntervalRejectsEmptyZeroAndExcessiveValues() {
        val preferences = AppPreferences()

        listOf("", "0", "86400001").forEach { value ->
            preferences.loopIntervalText = value
            assertFalse(preferences.isLoopIntervalValid)
            assertNull(preferences.loopIntervalOrNull)
            assertEquals(1000L, preferences.loopInterval)
        }
    }
}
