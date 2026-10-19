package com.yiaobang.kserial.model

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 用户可编辑的日志关键词高亮规则，支持逗号、分号和换行分隔。 */
class KeywordHighlightConfig {
    var enabled by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var keywordsText by mutableStateOf("")

    val keywords: List<String> by derivedStateOf {
        parseKeywords(keywordsText, ignoreCase)
    }

    fun setKeywords(keywords: List<String>) {
        keywordsText = keywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .let { if (ignoreCase) it.distinctBy(String::lowercase) else it.distinct() }
            .joinToString(", ")
    }

    companion object {
        fun parseKeywords(text: String, ignoreCase: Boolean = true): List<String> {
            val list = text
                .split(',', ';', '\n', '\r')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return if (ignoreCase) list.distinctBy { it.lowercase() } else list.distinct()
        }
    }
}
