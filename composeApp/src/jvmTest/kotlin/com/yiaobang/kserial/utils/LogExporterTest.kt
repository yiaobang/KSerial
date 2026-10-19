package com.yiaobang.kserial.utils

import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogExporterTest {
    private val logs = listOf(
        LogEntry("12:00:00", LogType.TX, "AT"),
        LogEntry("12:00:01", LogType.RX, "OK, \"ready\"")
    )

    @Test
    fun rendersReadableText() {
        assertEquals("[12:00:00] TX   AT\n[12:00:01] RX   OK, \"ready\"", LogExporter.render(logs, LogExportFormat.TEXT))
    }

    @Test
    fun escapesCsvFields() {
        val csv = LogExporter.render(logs, LogExportFormat.CSV)

        assertTrue(csv.startsWith("time,type,content\n"))
        assertTrue(csv.contains("\"OK, \"\"ready\"\"\""))
    }

    @Test
    fun writeOutputsMatchingContentToDisk() {
        val tempDir = java.nio.file.Files.createTempDirectory("kserial-log-export")
        try {
            val txtFile = tempDir.resolve("logs.txt")
            LogExporter.write(logs, LogExportFormat.TEXT, txtFile)
            assertEquals("[12:00:00] TX   AT\n[12:00:01] RX   OK, \"ready\"", java.nio.file.Files.readString(txtFile))

            val csvFile = tempDir.resolve("logs.csv")
            LogExporter.write(logs, LogExportFormat.CSV, csvFile)
            assertEquals(LogExporter.render(logs, LogExportFormat.CSV), java.nio.file.Files.readString(csvFile))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
