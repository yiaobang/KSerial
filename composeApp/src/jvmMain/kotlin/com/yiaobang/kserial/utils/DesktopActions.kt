package com.yiaobang.kserial.utils

import com.yiaobang.kserial.model.LogEntry
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object DesktopActions {
    fun copyText(text: String): Result<Unit> = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun exportLogs(
        logs: List<LogEntry>,
        format: LogExportFormat,
        dialogTitle: String
    ): Result<File?> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.SAVE).apply {
            file = "KSerial-log-$timestamp.${format.extension}"
            isVisible = true
        }
        val selected = dialog.file ?: return@runCatching null
        val fileName = if (selected.endsWith(".${format.extension}", ignoreCase = true)) {
            selected
        } else {
            "$selected.${format.extension}"
        }
        File(dialog.directory, fileName).also { file ->
            LogExporter.write(logs, format, file.toPath())
        }
    }
}
