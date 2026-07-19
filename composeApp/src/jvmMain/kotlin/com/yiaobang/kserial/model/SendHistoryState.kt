package com.yiaobang.kserial.model

import androidx.compose.runtime.mutableStateListOf

/**
 * 全工作区共享的发送历史，按最近使用排序并去重。
 * 同时维护上下键导航时的草稿，使用户浏览历史后还能回到原输入。
 */
class SendHistoryState(
    private val maxEntries: Int = 100
) {
    val entries = mutableStateListOf<String>()

    private var navigationIndex = -1
    private var navigationDraft = ""

    fun record(command: String) {
        if (command.isBlank()) return
        if (entries.firstOrNull() == command) {
            resetNavigation()
            return
        }
        // 已存在的命令移动到首位，而不是重复插入。
        entries.remove(command)
        entries.add(0, command)
        while (entries.size > maxEntries) entries.removeAt(entries.lastIndex)
        resetNavigation()
    }

    fun restore(commands: List<String>) {
        entries.clear()
        commands
            .filter { it.isNotBlank() }
            .distinct()
            .take(maxEntries)
            .let(entries::addAll)
        resetNavigation()
    }

    fun previous(currentInput: String): String? {
        if (entries.isEmpty()) return null
        // 第一次离开编辑中的文本时保存草稿，向下导航到底后恢复。
        if (navigationIndex == -1) navigationDraft = currentInput
        navigationIndex = (navigationIndex + 1).coerceAtMost(entries.lastIndex)
        return entries[navigationIndex]
    }

    fun next(): String? {
        if (navigationIndex == -1) return null
        navigationIndex--
        return if (navigationIndex == -1) navigationDraft else entries[navigationIndex]
    }

    fun remove(command: String) {
        entries.remove(command)
        resetNavigation()
    }

    fun clear() {
        entries.clear()
        resetNavigation()
    }

    fun resetNavigation() {
        navigationIndex = -1
        navigationDraft = ""
    }

    fun snapshot(): List<String> = entries.toList()
}
