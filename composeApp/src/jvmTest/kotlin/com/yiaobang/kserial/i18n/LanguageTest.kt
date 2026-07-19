package com.yiaobang.kserial.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTest {
    @Test
    fun detectsSupportedSystemLanguages() {
        assertEquals(Language.ZH, Language.fromLocale(Locale.SIMPLIFIED_CHINESE))
        assertEquals(Language.JA, Language.fromLocale(Locale.JAPANESE))
        assertEquals(Language.EN, Language.fromLocale(Locale.ENGLISH))
    }

    @Test
    fun fallsBackToEnglishForUnsupportedLanguages() {
        assertEquals(Language.EN, Language.fromLocale(Locale.FRENCH))
    }
}
