package com.yiaobang.kserial.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.yiaobang.kserial.resources.*
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/** 支持的界面语言及其资源语言标签。 */
enum class Language(
    val languageTag: String
) {
    ZH("zh"),
    EN("en"),
    JA("ja");

    fun next(): Language = entries[(ordinal + 1) % entries.size]

    fun applyAsDefaultLocale() {
        Locale.setDefault(Locale.forLanguageTag(languageTag))
    }

    companion object {
        fun fromLocale(locale: Locale = Locale.getDefault()): Language = when (locale.language.lowercase(Locale.ROOT)) {
            "ja" -> JA
            "zh" -> ZH
            else -> EN
        }
    }
}

/**
 * 当前语言下的完整文案快照。UI 只依赖该模型，不直接拼接业务文案，
 * 保证中文、英文和日文在同一组件中同步更新。
 */
data class AppStrings(
    val workspace: String,
    val sectionBasic: String,
    val sectionSimulator: String,
    val sectionGateway: String,
    val emptyCreatePage: String,
    val emptyLog: String,
    val sendInputHint: String,
    val panelConnection: String,
    val panelActions: String,
    val panelQuickSend: String,
    val panelOptions: String,
    val panelHighlight: String,
    val panelLog: String,
    val panelSendHistory: String,
    val panelProtocolTools: String,
    val panelConfig: String,
    val panelSimulator: String,
    val panelRoute: String,
    val panelEndpointPrefix: String,
    val labelSerialPort: String,
    val labelBaudRate: String,
    val labelDataBits: String,
    val labelStopBits: String,
    val labelParity: String,
    val labelFlowControl: String,
    val labelAdvancedSerialParameters: String,
    val stopBitsOptions: List<String>,
    val parityOptions: List<String>,
    val flowControlOptions: List<String>,
    val btnOpenPort: String,
    val btnClosePort: String,
    val btnClearReceive: String,
    val btnSendData: String,
    val btnClearSend: String,
    val btnNewWindow: String,
    val btnAnalogReply: String,
    val btnLoadConfig: String,
    val btnReloadConfig: String,
    val btnClearLog: String,
    val btnClearCounters: String,
    val btnResetLogFilter: String,
    val btnOpen: String,
    val btnClose: String,
    val btnFormatHex: String,
    val btnClearHistory: String,
    val btnAppendChecksum: String,
    val btnCopyLogs: String,
    val btnExportText: String,
    val btnExportCsv: String,
    val btnConfirm: String,
    val btnCancel: String,
    val optRealTime: String,
    val optHexDisplay: String,
    val optHexSend: String,
    val optLoopSend: String,
    val optSaveSend: String,
    val optSaveReceive: String,
    val optEnable: String,
    val optIgnoreCase: String,
    val optOnlyMatches: String,
    val optAutoScroll: String,
    val labelInterval: String,
    val labelMs: String,
    val labelTx: String,
    val labelRx: String,
    val labelStatusPort: String,
    val labelStatusSim: String,
    val labelRule: String,
    val labelMode: String,
    val labelSource: String,
    val labelTarget: String,
    val quickSendEmpty: String,
    val quickSendHint: String,
    val historyEmpty: String,
    val historyKeyboardHint: String,
    val actionUseHistory: String,
    val actionAddQuick: String,
    val actionRemoveHistory: String,
    val labelChecksum: String,
    val labelFrameMode: String,
    val labelFrameSize: String,
    val labelDelimiter: String,
    val frameModeFixed: String,
    val frameModeDelimiter: String,
    val checksumUnavailable: String,
    val frameUnavailable: String,
    val searchLog: String,
    val keywordSeparatorHint: String,
    val notConfigured: String,
    val notSelected: String,
    val noGatewayConfig: String,
    val loadGatewayConfigHint: String,
    val simulatorConfigDialogTitle: String,
    val gatewayConfigDialogTitle: String,
    val gatewayModeOneToOne: String,
    val gatewayModeOneToMany: String,
    val gatewayModeManyToMany: String,
    val statusConnected: String,
    val statusDisconnected: String,
    val errorLoopInterval: String,
    val actionPinWindow: String,
    val actionLanguage: String,
    val actionTheme: String,
    val actionMinimize: String,
    val actionCloseWindow: String,
    val actionNewTab: String,
    val actionDetachTab: String,
    val actionMergeTab: String,
    val actionCloseTab: String,
    val actionExpand: String,
    val actionCollapse: String,
    val actionCopyRow: String,
    val actionCopyContent: String,
    val actionClearLogs: String,
    val confirmClearLogsTitle: String,
    val confirmClearLogsMessage: String,
    val exportLogsDialogTitle: String,
    val logsCopied: String,
    val configValid: String,
    private val logCountPattern: String,
    private val searchPrefixPattern: String,
    private val panelEndpointPattern: String,
    private val connectionSummaryPattern: String,
    private val checksumResultPattern: String,
    private val frameResultPattern: String,
    private val logsExportedPattern: String,
    private val operationFailedPattern: String
) {
    fun logCount(visible: Int, total: Int): String = logCountPattern.formatLocalized(visible, total)
    fun searchPrefix(query: String): String = searchPrefixPattern.formatLocalized(query)
    fun panelEndpoint(name: String): String = panelEndpointPattern.formatLocalized(name)
    fun connectionSummary(baudRate: Int, frameFormat: String, flowControl: String): String =
        connectionSummaryPattern.formatLocalized(baudRate, frameFormat, flowControl)
    fun checksumResult(value: String): String = checksumResultPattern.formatLocalized(value)
    fun frameResult(frameCount: Int, remainingBytes: Int): String = frameResultPattern.formatLocalized(frameCount, remainingBytes)
    fun logsExported(path: String): String = logsExportedPattern.formatLocalized(path)
    fun operationFailed(reason: String): String = operationFailedPattern.formatLocalized(reason)
}

private fun String.formatLocalized(vararg args: Any): String = String.format(Locale.getDefault(), this, *args)

/** 子组件读取当前语言文案的 CompositionLocal。 */
val LocalAppStrings = staticCompositionLocalOf<AppStrings> {
    error("LocalAppStrings is not provided")
}

@Composable
fun ProvideAppStrings(
    language: Language,
    content: @Composable () -> Unit
) {
    // 设置默认 Locale 后，Compose Resources 会从对应 values-xx 目录读取文本。
    language.applyAsDefaultLocale()
    val strings = appStringsFromResources()
    CompositionLocalProvider(LocalAppStrings provides strings, content = content)
}

@Composable
private fun appStringsFromResources(): AppStrings {
    // 集中完成资源到强类型模型的映射，避免各 UI 文件遗漏某一种语言。
    return AppStrings(
        workspace = stringResource(Res.string.workspace),
        sectionBasic = stringResource(Res.string.section_basic),
        sectionSimulator = stringResource(Res.string.section_simulator),
        sectionGateway = stringResource(Res.string.section_gateway),
        emptyCreatePage = stringResource(Res.string.empty_create_page),
        emptyLog = stringResource(Res.string.empty_log),
        sendInputHint = stringResource(Res.string.send_input_hint),
        panelConnection = stringResource(Res.string.panel_connection),
        panelActions = stringResource(Res.string.panel_actions),
        panelQuickSend = stringResource(Res.string.panel_quick_send),
        panelOptions = stringResource(Res.string.panel_options),
        panelHighlight = stringResource(Res.string.panel_highlight),
        panelLog = stringResource(Res.string.panel_log),
        panelSendHistory = stringResource(Res.string.panel_send_history),
        panelProtocolTools = stringResource(Res.string.panel_protocol_tools),
        panelConfig = stringResource(Res.string.panel_config),
        panelSimulator = stringResource(Res.string.panel_simulator),
        panelRoute = stringResource(Res.string.panel_route),
        panelEndpointPrefix = stringResource(Res.string.panel_endpoint),
        labelSerialPort = stringResource(Res.string.label_serial_port),
        labelBaudRate = stringResource(Res.string.label_baud_rate),
        labelDataBits = stringResource(Res.string.label_data_bits),
        labelStopBits = stringResource(Res.string.label_stop_bits),
        labelParity = stringResource(Res.string.label_parity),
        labelFlowControl = stringResource(Res.string.label_flow_control),
        labelAdvancedSerialParameters = stringResource(Res.string.label_advanced_serial_parameters),
        stopBitsOptions = listOf("1", "1.5", "2"),
        parityOptions = listOf(
            stringResource(Res.string.option_none),
            stringResource(Res.string.option_odd),
            stringResource(Res.string.option_even),
            stringResource(Res.string.option_mark),
            stringResource(Res.string.option_space)
        ),
        flowControlOptions = listOf(
            stringResource(Res.string.option_none),
            "RTS/CTS",
            "DSR/DTR",
            "XON/XOFF"
        ),
        btnOpenPort = stringResource(Res.string.btn_open_port),
        btnClosePort = stringResource(Res.string.btn_close_port),
        btnClearReceive = stringResource(Res.string.btn_clear_receive),
        btnSendData = stringResource(Res.string.btn_send_data),
        btnClearSend = stringResource(Res.string.btn_clear_send),
        btnNewWindow = stringResource(Res.string.btn_new_window),
        btnAnalogReply = stringResource(Res.string.btn_analog_reply),
        btnLoadConfig = stringResource(Res.string.btn_load_config),
        btnReloadConfig = stringResource(Res.string.btn_reload_config),
        btnClearLog = stringResource(Res.string.btn_clear_log),
        btnClearCounters = stringResource(Res.string.btn_clear_counters),
        btnResetLogFilter = stringResource(Res.string.btn_reset_log_filter),
        btnOpen = stringResource(Res.string.btn_open),
        btnClose = stringResource(Res.string.btn_close),
        btnFormatHex = stringResource(Res.string.btn_format_hex),
        btnClearHistory = stringResource(Res.string.btn_clear_history),
        btnAppendChecksum = stringResource(Res.string.btn_append_checksum),
        btnCopyLogs = stringResource(Res.string.btn_copy_logs),
        btnExportText = stringResource(Res.string.btn_export_text),
        btnExportCsv = stringResource(Res.string.btn_export_csv),
        btnConfirm = stringResource(Res.string.btn_confirm),
        btnCancel = stringResource(Res.string.btn_cancel),
        optRealTime = stringResource(Res.string.opt_real_time),
        optHexDisplay = stringResource(Res.string.opt_hex_display),
        optHexSend = stringResource(Res.string.opt_hex_send),
        optLoopSend = stringResource(Res.string.opt_loop_send),
        optSaveSend = stringResource(Res.string.opt_save_send),
        optSaveReceive = stringResource(Res.string.opt_save_receive),
        optEnable = stringResource(Res.string.opt_enable),
        optIgnoreCase = stringResource(Res.string.opt_ignore_case),
        optOnlyMatches = stringResource(Res.string.opt_only_matches),
        optAutoScroll = stringResource(Res.string.opt_auto_scroll),
        labelInterval = stringResource(Res.string.label_interval),
        labelMs = stringResource(Res.string.label_ms),
        labelTx = stringResource(Res.string.label_tx),
        labelRx = stringResource(Res.string.label_rx),
        labelStatusPort = stringResource(Res.string.label_status_port),
        labelStatusSim = stringResource(Res.string.label_status_sim),
        labelRule = stringResource(Res.string.label_rule),
        labelMode = stringResource(Res.string.label_mode),
        labelSource = stringResource(Res.string.label_source),
        labelTarget = stringResource(Res.string.label_target),
        quickSendEmpty = stringResource(Res.string.quick_send_empty),
        quickSendHint = stringResource(Res.string.quick_send_hint),
        historyEmpty = stringResource(Res.string.history_empty),
        historyKeyboardHint = stringResource(Res.string.history_keyboard_hint),
        actionUseHistory = stringResource(Res.string.action_use_history),
        actionAddQuick = stringResource(Res.string.action_add_quick),
        actionRemoveHistory = stringResource(Res.string.action_remove_history),
        labelChecksum = stringResource(Res.string.label_checksum),
        labelFrameMode = stringResource(Res.string.label_frame_mode),
        labelFrameSize = stringResource(Res.string.label_frame_size),
        labelDelimiter = stringResource(Res.string.label_delimiter),
        frameModeFixed = stringResource(Res.string.frame_mode_fixed),
        frameModeDelimiter = stringResource(Res.string.frame_mode_delimiter),
        checksumUnavailable = stringResource(Res.string.checksum_unavailable),
        frameUnavailable = stringResource(Res.string.frame_unavailable),
        searchLog = stringResource(Res.string.search_log),
        keywordSeparatorHint = stringResource(Res.string.keyword_separator_hint),
        notConfigured = stringResource(Res.string.not_configured),
        notSelected = stringResource(Res.string.not_selected),
        noGatewayConfig = stringResource(Res.string.no_gateway_config),
        loadGatewayConfigHint = stringResource(Res.string.load_gateway_config_hint),
        simulatorConfigDialogTitle = stringResource(Res.string.simulator_config_dialog_title),
        gatewayConfigDialogTitle = stringResource(Res.string.gateway_config_dialog_title),
        gatewayModeOneToOne = stringResource(Res.string.gateway_mode_one_to_one),
        gatewayModeOneToMany = stringResource(Res.string.gateway_mode_one_to_many),
        gatewayModeManyToMany = stringResource(Res.string.gateway_mode_many_to_many),
        statusConnected = stringResource(Res.string.status_connected),
        statusDisconnected = stringResource(Res.string.status_disconnected),
        errorLoopInterval = stringResource(Res.string.error_loop_interval),
        actionPinWindow = stringResource(Res.string.action_pin_window),
        actionLanguage = stringResource(Res.string.action_language),
        actionTheme = stringResource(Res.string.action_theme),
        actionMinimize = stringResource(Res.string.action_minimize),
        actionCloseWindow = stringResource(Res.string.action_close_window),
        actionNewTab = stringResource(Res.string.action_new_tab),
        actionDetachTab = stringResource(Res.string.action_detach_tab),
        actionMergeTab = stringResource(Res.string.action_merge_tab),
        actionCloseTab = stringResource(Res.string.action_close_tab),
        actionExpand = stringResource(Res.string.action_expand),
        actionCollapse = stringResource(Res.string.action_collapse),
        actionCopyRow = stringResource(Res.string.action_copy_row),
        actionCopyContent = stringResource(Res.string.action_copy_content),
        actionClearLogs = stringResource(Res.string.action_clear_logs),
        confirmClearLogsTitle = stringResource(Res.string.confirm_clear_logs_title),
        confirmClearLogsMessage = stringResource(Res.string.confirm_clear_logs_message),
        exportLogsDialogTitle = stringResource(Res.string.export_logs_dialog_title),
        logsCopied = stringResource(Res.string.logs_copied),
        configValid = stringResource(Res.string.config_valid),
        logCountPattern = stringResource(Res.string.log_count),
        searchPrefixPattern = stringResource(Res.string.search_prefix),
        panelEndpointPattern = stringResource(Res.string.panel_endpoint),
        connectionSummaryPattern = stringResource(Res.string.connection_summary),
        checksumResultPattern = stringResource(Res.string.checksum_result),
        frameResultPattern = stringResource(Res.string.frame_result),
        logsExportedPattern = stringResource(Res.string.logs_exported),
        operationFailedPattern = stringResource(Res.string.operation_failed)
    )
}
