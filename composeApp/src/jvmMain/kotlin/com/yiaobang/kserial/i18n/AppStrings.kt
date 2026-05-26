package com.yiaobang.kserial.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.yiaobang.kserial.resources.Res
import com.yiaobang.kserial.resources.btn_analog_reply
import com.yiaobang.kserial.resources.btn_clear_counters
import com.yiaobang.kserial.resources.btn_clear_log
import com.yiaobang.kserial.resources.btn_clear_receive
import com.yiaobang.kserial.resources.btn_clear_send
import com.yiaobang.kserial.resources.btn_close
import com.yiaobang.kserial.resources.btn_close_port
import com.yiaobang.kserial.resources.btn_load_config
import com.yiaobang.kserial.resources.btn_new_window
import com.yiaobang.kserial.resources.btn_open
import com.yiaobang.kserial.resources.btn_open_port
import com.yiaobang.kserial.resources.btn_reload_config
import com.yiaobang.kserial.resources.btn_reset_log_filter
import com.yiaobang.kserial.resources.btn_send_data
import com.yiaobang.kserial.resources.empty_create_page
import com.yiaobang.kserial.resources.gateway_config_dialog_title
import com.yiaobang.kserial.resources.gateway_mode_many_to_many
import com.yiaobang.kserial.resources.gateway_mode_one_to_many
import com.yiaobang.kserial.resources.gateway_mode_one_to_one
import com.yiaobang.kserial.resources.label_baud_rate
import com.yiaobang.kserial.resources.label_data_bits
import com.yiaobang.kserial.resources.label_flow_control
import com.yiaobang.kserial.resources.label_interval
import com.yiaobang.kserial.resources.keyword_separator_hint
import com.yiaobang.kserial.resources.label_mode
import com.yiaobang.kserial.resources.label_ms
import com.yiaobang.kserial.resources.label_parity
import com.yiaobang.kserial.resources.label_rule
import com.yiaobang.kserial.resources.label_rx
import com.yiaobang.kserial.resources.label_serial_port
import com.yiaobang.kserial.resources.label_source
import com.yiaobang.kserial.resources.label_status_port
import com.yiaobang.kserial.resources.label_status_sim
import com.yiaobang.kserial.resources.label_stop_bits
import com.yiaobang.kserial.resources.label_target
import com.yiaobang.kserial.resources.label_tx
import com.yiaobang.kserial.resources.load_gateway_config_hint
import com.yiaobang.kserial.resources.log_count
import com.yiaobang.kserial.resources.no_gateway_config
import com.yiaobang.kserial.resources.not_configured
import com.yiaobang.kserial.resources.not_selected
import com.yiaobang.kserial.resources.opt_enable
import com.yiaobang.kserial.resources.opt_hex_display
import com.yiaobang.kserial.resources.opt_hex_send
import com.yiaobang.kserial.resources.opt_ignore_case
import com.yiaobang.kserial.resources.opt_loop_send
import com.yiaobang.kserial.resources.opt_auto_scroll
import com.yiaobang.kserial.resources.opt_only_matches
import com.yiaobang.kserial.resources.opt_real_time
import com.yiaobang.kserial.resources.opt_save_receive
import com.yiaobang.kserial.resources.opt_save_send
import com.yiaobang.kserial.resources.panel_actions
import com.yiaobang.kserial.resources.panel_config
import com.yiaobang.kserial.resources.panel_connection
import com.yiaobang.kserial.resources.panel_endpoint
import com.yiaobang.kserial.resources.panel_highlight
import com.yiaobang.kserial.resources.panel_log
import com.yiaobang.kserial.resources.panel_options
import com.yiaobang.kserial.resources.panel_quick_send
import com.yiaobang.kserial.resources.panel_route
import com.yiaobang.kserial.resources.panel_simulator
import com.yiaobang.kserial.resources.quick_send_empty
import com.yiaobang.kserial.resources.quick_send_hint
import com.yiaobang.kserial.resources.search_log
import com.yiaobang.kserial.resources.search_prefix
import com.yiaobang.kserial.resources.section_basic
import com.yiaobang.kserial.resources.section_gateway
import com.yiaobang.kserial.resources.section_simulator
import com.yiaobang.kserial.resources.simulator_config_dialog_title
import com.yiaobang.kserial.resources.workspace
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

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
}

data class AppStrings(
    val workspace: String,
    val sectionBasic: String,
    val sectionSimulator: String,
    val sectionGateway: String,
    val emptyCreatePage: String,
    val panelConnection: String,
    val panelActions: String,
    val panelQuickSend: String,
    val panelOptions: String,
    val panelHighlight: String,
    val panelLog: String,
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
    private val logCountPattern: String,
    private val searchPrefixPattern: String,
    private val panelEndpointPattern: String
) {
    fun logCount(visible: Int, total: Int): String = logCountPattern.formatLocalized(visible, total)
    fun searchPrefix(query: String): String = searchPrefixPattern.formatLocalized(query)
    fun panelEndpoint(name: String): String = panelEndpointPattern.formatLocalized(name)
}

private fun String.formatLocalized(vararg args: Any): String = String.format(Locale.getDefault(), this, *args)

val LocalAppStrings = staticCompositionLocalOf<AppStrings> {
    error("LocalAppStrings is not provided")
}

@Composable
fun ProvideAppStrings(
    language: Language,
    content: @Composable () -> Unit
) {
    language.applyAsDefaultLocale()
    val strings = appStringsFromResources()
    CompositionLocalProvider(LocalAppStrings provides strings, content = content)
}

@Composable
private fun appStringsFromResources(): AppStrings {
    return AppStrings(
        workspace = stringResource(Res.string.workspace),
        sectionBasic = stringResource(Res.string.section_basic),
        sectionSimulator = stringResource(Res.string.section_simulator),
        sectionGateway = stringResource(Res.string.section_gateway),
        emptyCreatePage = stringResource(Res.string.empty_create_page),
        panelConnection = stringResource(Res.string.panel_connection),
        panelActions = stringResource(Res.string.panel_actions),
        panelQuickSend = stringResource(Res.string.panel_quick_send),
        panelOptions = stringResource(Res.string.panel_options),
        panelHighlight = stringResource(Res.string.panel_highlight),
        panelLog = stringResource(Res.string.panel_log),
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
        stopBitsOptions = listOf("1", "1.5", "2"),
        parityOptions = listOf("None", "Odd", "Even", "Mark", "Space"),
        flowControlOptions = listOf("None", "RTS/CTS", "DSR/DTR", "XON/XOFF"),
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
        logCountPattern = stringResource(Res.string.log_count),
        searchPrefixPattern = stringResource(Res.string.search_prefix),
        panelEndpointPattern = stringResource(Res.string.panel_endpoint)
    )
}
