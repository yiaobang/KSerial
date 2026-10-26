package com.yiaobang.kserial.utils

import com.yiaobang.kserial.model.LogEntry
import java.nio.file.Files
import java.nio.file.Path

/** 用户可选择的会话日志导出格式。 */
enum class LogExportFormat(val extension: String) {
    TEXT("txt"),
    CSV("csv")
}

/** 将当前可见日志渲染并写入文本或 RFC 风格转义的 CSV 文件。 */
object LogExporter {
    fun render(logs: List<LogEntry>, format: LogExportFormat): String = when (format) {
        LogExportFormat.TEXT -> logs.joinToString("\n") { log ->
            "[${log.time}] ${log.type.name.padEnd(4)} ${log.content}"
        }
        LogExportFormat.CSV -> buildString {
            appendLine("time,type,content")
            logs.forEach { log ->
                append(csv(log.time))
                append(',')
                append(csv(log.type.name))
                append(',')
                appendLine(csv(log.content))
            }
        }.trimEnd('\n', '\r')
    }

    fun write(logs: List<LogEntry>, format: LogExportFormat, path: Path): Path {
        path.parent?.let { parent -> Files.createDirectories(parent) }
        // 直接复用 render() 确保磁盘内容与内存渲染完全一致（换行符统一为 \n）
        Files.writeString(path, render(logs, format))
        return path
    }

    /** CSV 字段统一加引号，字段内部双引号按规范写成两个双引号。 */
    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
