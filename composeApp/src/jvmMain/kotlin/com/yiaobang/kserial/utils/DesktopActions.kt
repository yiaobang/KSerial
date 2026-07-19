package com.yiaobang.kserial.utils

import com.yiaobang.kserial.model.LogEntry
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FilenameFilter
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO

object DesktopActions {
    private const val APP_ICON_RESOURCE =
        "composeResources/com.yiaobang.kserial.resources/drawable/app_icon.png"

    private val appIcon: Image? by lazy {
        val image = DesktopActions::class.java.classLoader
            .getResourceAsStream(APP_ICON_RESOURCE)
            ?.use(ImageIO::read)
        if (image != null) {
            runCatching {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.iconImage = image
            }
        }
        image
    }

    fun copyText(text: String): Result<Unit> = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun selectConfigFile(dialogTitle: String): File? {
        AppPaths.initializeConfigDirectory()
        return showFileDialog(
            title = dialogTitle,
            mode = FileDialog.LOAD,
            initialDirectory = AppPaths.preferredConfigDirectory(),
            initialFile = "*.conf",
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".conf", ignoreCase = true) || name.endsWith(".hocon", ignoreCase = true)
            }
        )
    }

    fun exportLogs(
        logs: List<LogEntry>,
        format: LogExportFormat,
        dialogTitle: String
    ): Result<File?> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val selected = showFileDialog(
            title = dialogTitle,
            mode = FileDialog.SAVE,
            initialFile = "KSerial-log-$timestamp.${format.extension}"
        ) ?: return@runCatching null
        val output = if (selected.name.endsWith(".${format.extension}", ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile, "${selected.name}.${format.extension}")
        }
        output.also { LogExporter.write(logs, format, it.toPath()) }
    }

    internal fun appIconAvailable(): Boolean = appIcon != null

    private fun showFileDialog(
        title: String,
        mode: Int,
        initialDirectory: File? = null,
        initialFile: String? = null,
        filenameFilter: FilenameFilter? = null
    ): File? {
        val owner = Frame("KSerial").apply {
            appIcon?.let { iconImage = it }
        }
        val dialog = FileDialog(owner, title, mode).apply {
            appIcon?.let { iconImages = listOf(it) }
            initialDirectory?.let { directory = it.absolutePath + File.separator }
            initialFile?.let { file = it }
            this.filenameFilter = filenameFilter
        }
        return try {
            dialog.isVisible = true
            dialog.file?.let { File(dialog.directory, it) }
        } finally {
            dialog.dispose()
            owner.dispose()
        }
    }
}
