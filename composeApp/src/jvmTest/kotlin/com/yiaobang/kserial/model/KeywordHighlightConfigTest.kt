package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertEquals

class KeywordHighlightConfigTest {
    @Test
    fun parsesAndDeduplicatesKeywords() {
        val keywords = KeywordHighlightConfig.parseKeywords("ERROR, warn; error\nOK")

        assertEquals(listOf("ERROR", "warn", "OK"), keywords)
    }

    @Test
    fun setKeywordsDropsBlankValues() {
        val config = KeywordHighlightConfig()

        config.setKeywords(listOf(" ERROR ", "", "error", "OK"))

        assertEquals("ERROR, OK", config.keywordsText)
    }
}
