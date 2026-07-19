package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class SettingsLoadResult(
    val settings: PersistedSettings,
    val warning: String? = null
)

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
            .setComments(true)
            .setOriginComments(false)
        val generated = ConfigFactory.parseMap(values).withBuiltInComments()
        val existing = if (Files.exists(settingsFile)) {
            runCatching { ConfigFactory.parseFile(settingsFile.toFile()) }.getOrNull()
        } else {
            null
        }
        val merged = existing?.let { current ->
            generated.entrySet().fold(current) { config, entry ->
                val existingComments = if (config.hasPath(entry.key)) {
                    runCatching { config.getValue(entry.key).origin().comments() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val comments = (entry.value.origin().comments() + existingComments.filterNot { it in LEGACY_HEADER_COMMENTS })
                    .distinct()
                val documentedValue = entry.value.withOrigin(entry.value.origin().withComments(comments))
                config.withValue(entry.key, documentedValue)
            }
        } ?: generated
        val body = merged.root().render(options)
        return "$body\n"
    }

    companion object {
        fun defaultSettingsPath(): Path {
            val os = System.getProperty("os.name").lowercase()
            val userHome = Path.of(System.getProperty("user.home"))
            return when {
                os.contains("win") -> {
                    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    (appData?.let(Path::of) ?: userHome.resolve("AppData/Roaming"))
                        .resolve("KSerial/settings.conf")
                }
                os.contains("mac") -> userHome.resolve("Library/Application Support/KSerial/settings.conf")
                else -> {
                    val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                    (xdg?.let(Path::of) ?: userHome.resolve(".config"))
                        .resolve("kserial/settings.conf")
                }
            }
        }
    }
}

private val LEGACY_HEADER_COMMENTS = setOf(
    "KSerial global settings",
    "HOCON supports comments beginning with # or //.",
    "The send input, loop-send switch, and loop interval are intentionally not persisted.",
    "The selected serial port is restored but never opened automatically."
)

private val SETTINGS_COMMENTS = linkedMapOf(
    "appearance.darkTheme" to listOf(
        "KSerial 全局设置 / KSerial global settings / KSerial グローバル設定",
        "支持 # 和 // 注释；保存时保留自定义注释和未知字段 / Supports # and // comments; custom comments and unknown fields are preserved on save / # と // のコメントに対応し、保存時も独自コメントと未知の項目を保持します",
        "不会保存发送输入、循环发送开关及间隔 / Send input, loop-send switch, and interval are not persisted / 送信入力、繰り返し送信スイッチ、送信間隔は保存しません",
        "仅恢复所选端口，不会自动打开串口 / The selected port is restored but never opened automatically / 選択ポートは復元しますが、自動では開きません",
        "外观与语言 / Appearance and language / 外観と言語",
        "是否使用深色主题 / Use the dark theme / ダークテーマを使用するか"
    ),
    "appearance.alwaysOnTop" to listOf(
        "窗口是否保持最前 / Keep windows always on top / ウィンドウを常に最前面に表示するか"
    ),
    "appearance.language" to listOf(
        "界面语言：zh=中文，en=English，ja=日本語 / UI language: zh, en, or ja / UI 言語：zh、en、ja"
    ),
    "serial.selectedPort" to listOf(
        "串口参数 / Serial port parameters / シリアルポート設定",
        "最后选择的端口名称；只恢复选择，不自动连接 / Last selected port; restored without auto-connect / 最後に選択したポート。選択のみ復元し、自動接続はしません"
    ),
    "serial.baudRate" to listOf(
        "波特率 / Baud rate / ボーレート"
    ),
    "serial.dataBits" to listOf(
        "数据位：5 至 8 / Data bits: 5 to 8 / データビット：5～8"
    ),
    "serial.stopBitsIndex" to listOf(
        "停止位索引：0=1，1=1.5，2=2 / Stop-bits index: 0=1, 1=1.5, 2=2 / ストップビットの索引：0=1、1=1.5、2=2"
    ),
    "serial.parityIndex" to listOf(
        "校验位索引：0=None，1=Odd，2=Even，3=Mark，4=Space / Parity index: 0=None, 1=Odd, 2=Even, 3=Mark, 4=Space / パリティの索引：0=None、1=Odd、2=Even、3=Mark、4=Space"
    ),
    "serial.flowControlIndex" to listOf(
        "流控索引：0=None，1=RTS/CTS，2=DSR/DTR，3=XON/XOFF / Flow-control index: 0=None, 1=RTS/CTS, 2=DSR/DTR, 3=XON/XOFF / フロー制御の索引：0=None、1=RTS/CTS、2=DSR/DTR、3=XON/XOFF"
    ),
    "preferences.showRealTime" to listOf(
        "收发、显示与日志偏好 / Communication, display, and log preferences / 送受信・表示・ログ設定",
        "日志是否显示实时时间 / Show timestamps in logs / ログに時刻を表示するか"
    ),
    "preferences.hexDisplay" to listOf(
        "接收内容是否以 HEX 显示 / Display received data as HEX / 受信データを HEX 表示するか"
    ),
    "preferences.hexSend" to listOf(
        "发送输入是否按 HEX 解析 / Interpret send input as HEX / 送信入力を HEX として解釈するか"
    ),
    "preferences.saveSend" to listOf(
        "是否保存发送数据 / Save transmitted data / 送信データを保存するか"
    ),
    "preferences.saveReceive" to listOf(
        "是否保存接收数据 / Save received data / 受信データを保存するか"
    ),
    "preferences.quickCommandsText" to listOf(
        "快捷发送命令；每行一条，可使用转义字符 / Quick-send commands, one per line; escape sequences are supported / クイック送信コマンド。1 行に 1 件、エスケープシーケンス対応"
    ),
    "preferences.highlightEnabled" to listOf(
        "是否启用关键词高亮 / Enable keyword highlighting / キーワード強調を有効にするか"
    ),
    "preferences.highlightIgnoreCase" to listOf(
        "关键词匹配是否忽略大小写 / Ignore case when matching keywords / キーワード照合時に大文字小文字を無視するか"
    ),
    "preferences.highlightKeywords" to listOf(
        "高亮关键词列表文本 / Highlight keyword list / 強調するキーワード一覧"
    ),
    "preferences.logQuery" to listOf(
        "最后使用的日志搜索内容 / Last log search query / 最後に使用したログ検索文字列"
    ),
    "preferences.logOnlyMatches" to listOf(
        "是否只显示搜索匹配项 / Show only matching log entries / 一致したログだけを表示するか"
    ),
    "preferences.logAutoScroll" to listOf(
        "日志是否自动滚动到底部 / Automatically scroll logs to the bottom / ログを自動的に末尾までスクロールするか"
    ),
    "preferences.logShowTx" to listOf(
        "是否显示 TX 日志 / Show TX logs / TX ログを表示するか"
    ),
    "preferences.logShowRx" to listOf(
        "是否显示 RX 日志 / Show RX logs / RX ログを表示するか"
    ),
    "preferences.logShowInfo" to listOf(
        "是否显示 INFO 日志 / Show INFO logs / INFO ログを表示するか"
    ),
    "schemaVersion" to listOf(
        "配置结构版本，请勿随意修改 / Settings schema version; do not change manually / 設定スキーマのバージョン。手動で変更しないでください"
    ),
    "sendHistory" to listOf(
        "发送历史：最新在前，自动去重，最多 100 条 / Send history: newest first, deduplicated, maximum 100 entries / 送信履歴：新しい順、重複除外、最大 100 件"
    )
)

private fun Config.withBuiltInComments(): Config = SETTINGS_COMMENTS.entries.fold(this) { config, (path, comments) ->
    val value = config.getValue(path)
    config.withValue(path, value.withOrigin(value.origin().withComments(comments)))
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
