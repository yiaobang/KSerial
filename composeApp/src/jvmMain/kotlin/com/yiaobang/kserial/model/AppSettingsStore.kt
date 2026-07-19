package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.yiaobang.kserial.utils.AppPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** 加载失败时返回安全默认值，并把可展示的原因作为 warning 交给 UI。 */
data class SettingsLoadResult(
    val settings: PersistedSettings,
    val warning: String? = null
)

/**
 * settings.conf 的读取与原子写入入口。
 * 保存时保留应用不认识的现有字段，使用户自定义扩展不会被一次设置保存全部抹掉。
 */
class AppSettingsStore(
    val settingsFile: Path = defaultSettingsPath()
) {
    fun load(): SettingsLoadResult {
        if (!Files.exists(settingsFile)) return SettingsLoadResult(PersistedSettings())
        return runCatching {
            val config = ConfigFactory.parseFile(settingsFile.toFile()).resolve()
            SettingsLoadResult(config.toPersistedSettings())
        }.getOrElse { error ->
            SettingsLoadResult(
                settings = PersistedSettings(),
                warning = "Settings could not be loaded: ${error.message ?: error::class.simpleName}"
            )
        }
    }

    fun save(settings: PersistedSettings): Result<Path> = runCatching {
        val parent = settingsFile.parent
        if (parent != null) Files.createDirectories(parent)
        val temporary = settingsFile.resolveSibling("${settingsFile.fileName}.tmp")
        Files.writeString(temporary, render(settings))
        // 同目录临时文件 + 原子替换可避免进程中断留下半份配置；文件系统不支持时再普通替换。
        runCatching {
            Files.move(
                temporary,
                settingsFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING)
        }
        settingsFile
    }

    private fun render(settings: PersistedSettings): String {
        val values = linkedMapOf<String, Any>(
            "schemaVersion" to settings.schemaVersion,
            "appearance" to linkedMapOf(
                "darkTheme" to settings.appearance.darkTheme,
                "alwaysOnTop" to settings.appearance.alwaysOnTop,
                "language" to settings.appearance.language
            ),
            "serial" to linkedMapOf(
                "selectedPort" to settings.serial.selectedPort,
                "baudRate" to settings.serial.baudRate,
                "dataBits" to settings.serial.dataBits,
                "stopBitsIndex" to settings.serial.stopBitsIndex,
                "parityIndex" to settings.serial.parityIndex,
                "flowControlIndex" to settings.serial.flowControlIndex
            ),
            "preferences" to linkedMapOf(
                "showRealTime" to settings.preferences.showRealTime,
                "hexDisplay" to settings.preferences.hexDisplay,
                "hexSend" to settings.preferences.hexSend,
                "saveSend" to settings.preferences.saveSend,
                "saveReceive" to settings.preferences.saveReceive,
                "quickCommandsText" to settings.preferences.quickCommandsText,
                "highlightEnabled" to settings.preferences.highlightEnabled,
                "highlightIgnoreCase" to settings.preferences.highlightIgnoreCase,
                "highlightKeywords" to settings.preferences.highlightKeywords,
                "logQuery" to settings.preferences.logQuery,
                "logOnlyMatches" to settings.preferences.logOnlyMatches,
                "logAutoScroll" to settings.preferences.logAutoScroll,
                "logShowTx" to settings.preferences.logShowTx,
                "logShowRx" to settings.preferences.logShowRx,
                "logShowInfo" to settings.preferences.logShowInfo
            ),
            "sendHistory" to settings.sendHistory
        )
        val options = ConfigRenderOptions.defaults()
            .setJson(false)
            .setFormatted(true)
            .setComments(false)
            .setOriginComments(false)
        val generated = ConfigFactory.parseMap(values)
        val existing = if (Files.exists(settingsFile)) {
            runCatching { ConfigFactory.parseFile(settingsFile.toFile()) }.getOrNull()
        } else {
            null
        }
        // 只覆盖本版本拥有的字段，未知字段仍从 current 中保留。
        val merged = existing?.let { current ->
            generated.entrySet().fold(current) { config, entry ->
                config.withValue(entry.key, entry.value)
            }
        } ?: generated
        return "${merged.root().render(options)}\n"
    }

    companion object {
        fun defaultSettingsPath(): Path = AppPaths.SETTINGS_FILE.toPath()
    }
}

private fun Config.toPersistedSettings(): PersistedSettings = PersistedSettings(
    schemaVersion = intOr("schemaVersion", 1),
    appearance = PersistedAppearance(
        darkTheme = booleanOr("appearance.darkTheme", false),
        alwaysOnTop = booleanOr("appearance.alwaysOnTop", false),
        language = stringOr("appearance.language", "")
    ),
    serial = PersistedSerialSettings(
        selectedPort = stringOr("serial.selectedPort", ""),
        baudRate = intOr("serial.baudRate", 9600),
        dataBits = intOr("serial.dataBits", 8),
        stopBitsIndex = intOr("serial.stopBitsIndex", 0),
        parityIndex = intOr("serial.parityIndex", 0),
        flowControlIndex = intOr("serial.flowControlIndex", 0)
    ),
    preferences = PersistedPreferences(
        showRealTime = booleanOr("preferences.showRealTime", true),
        hexDisplay = booleanOr("preferences.hexDisplay", false),
        hexSend = booleanOr("preferences.hexSend", false),
        saveSend = booleanOr("preferences.saveSend", false),
        saveReceive = booleanOr("preferences.saveReceive", false),
        quickCommandsText = stringOr("preferences.quickCommandsText", "AT\\r\\n\nAT+VERSION\\r\\n"),
        highlightEnabled = booleanOr("preferences.highlightEnabled", false),
        highlightIgnoreCase = booleanOr("preferences.highlightIgnoreCase", true),
        highlightKeywords = stringOr("preferences.highlightKeywords", ""),
        logQuery = stringOr("preferences.logQuery", ""),
        logOnlyMatches = booleanOr("preferences.logOnlyMatches", false),
        logAutoScroll = booleanOr("preferences.logAutoScroll", true),
        logShowTx = booleanOr("preferences.logShowTx", true),
        logShowRx = booleanOr("preferences.logShowRx", true),
        logShowInfo = booleanOr("preferences.logShowInfo", true)
    ),
    sendHistory = if (hasPath("sendHistory")) runCatching { getStringList("sendHistory") }.getOrDefault(emptyList()) else emptyList()
)

private fun Config.stringOr(path: String, default: String): String =
    if (hasPath(path)) runCatching { getString(path) }.getOrDefault(default) else default

private fun Config.intOr(path: String, default: Int): Int =
    if (hasPath(path)) runCatching { getInt(path) }.getOrDefault(default) else default

private fun Config.booleanOr(path: String, default: Boolean): Boolean =
    if (hasPath(path)) runCatching { getBoolean(path) }.getOrDefault(default) else default
