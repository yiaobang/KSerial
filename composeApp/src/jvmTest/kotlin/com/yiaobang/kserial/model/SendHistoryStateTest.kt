package com.yiaobang.kserial.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SendHistoryStateTest {
    @Test
    fun recordsNewestFirstAndDeduplicates() {
        val history = SendHistoryState(maxEntries = 3)

        history.record("A")
        history.record("B")
        history.record("A")
        history.record("C")
        history.record("D")

        assertEquals(listOf("D", "C", "A"), history.snapshot())
    }

    @Test
    fun navigationReturnsToOriginalDraft() {
        val history = SendHistoryState().apply { restore(listOf("new", "old")) }

        assertEquals("new", history.previous("draft"))
        assertEquals("old", history.previous("new"))
        assertEquals("new", history.next())
        assertEquals("draft", history.next())
        assertNull(history.next())
    }
}
