package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 用户可编辑的日志关键词高亮规则，支持逗号、分号和换行分隔。 */
class KeywordHighlightConfig {
    var enabled by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var keywordsText by mutableStateOf("")

    val keywords: List<String>
        get() = parseKeywords(keywordsText)

    fun setKeywords(keywords: List<String>) {
        // 以不区分大小写的方式去重，避免同一关键词产生重复高亮范围。
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
