package com.yiaobang.kserial.model

import androidx.compose.runtime.mutableStateListOf

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
