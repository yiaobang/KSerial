package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class KeywordHighlightConfig {
    var enabled by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var keywordsText by mutableStateOf("")

    val keywords: List<String>
        get() = parseKeywords(keywordsText)

    fun setKeywords(keywords: List<String>) {
        keywordsText = keywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
    }

    companion object {
        fun parseKeywords(text: String): List<String> {
            return text
                .split(',', ';', '\n', '\r')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
        }
    }
}
