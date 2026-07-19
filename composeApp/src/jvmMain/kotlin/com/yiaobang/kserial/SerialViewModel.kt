package com.yiaobang.kserial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.model.AppPreferences
import com.yiaobang.kserial.model.ConfigIssue
import com.yiaobang.kserial.model.ConfigIssueSeverity
import com.yiaobang.kserial.model.ConfigValidator
import com.yiaobang.kserial.model.DataWriteFile
import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.model.SendHistoryState
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.TextEscapeParser
import com.yiaobang.kserial.utils.ChecksumAlgorithm
import com.yiaobang.kserial.utils.ChecksumUtils
import com.yiaobang.kserial.utils.toHexString
import com.yiaobang.kserial.utils.toUtf8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

private data class SerialConnectionSettings(
    val port: String,
    val baudRate: Int,
    val dataBits: Int,
    val stopBitsIndex: Int,
    val parityIndex: Int,
    val flowControlIndex: Int
)

/**
 * 串口视图模型类
 * 职责：业务逻辑协调层，连接 UI 层与底层通信层
 * 串口参数由 [config] 管理，UI 偏好由 [prefs] 管理
 */
class SerialViewModel(
    private val autoConnectFirstPort: Boolean = true,
    private val syncOnConfigChange: Boolean = true,
    val sendHistory: SendHistoryState = SendHistoryState()
) : ViewModel() {

    // ---- 底层通信实例（每个 VM 独立，实现窗口级隔离）----
    val serialComm = SerialComm()

    // ---- 状态持有者（职责分离）----
    /** 串口连接参数 */
    val config = SerialPortConfig()

    /** UI 显示偏好 */
    val prefs = AppPreferences()

    // ---- 串口列表 ----
    var availablePorts by mutableStateOf(emptyList<String>())

    // ---- 连接状态 ----
    var isPortOpen by mutableStateOf(false)

    // ---- 模拟状态 ----
    var isSimulating by mutableStateOf(false)
    var simulatorConfigName by mutableStateOf("")
    val configIssues = androidx.compose.runtime.mutableStateListOf<ConfigIssue>()

    // ---- 发送区文本（提升到 VM 供循环发送读取）----
    var inputText by mutableStateOf("")

    // ---- 统计计数 ----
    var txCount by mutableLongStateOf(0L)
    var rxCount by mutableLongStateOf(0L)

    // ---- 日志列表 ----
    val logs = androidx.compose.runtime.mutableStateListOf<LogEntry>()
    private var nextLogId = 1L

    // ---- 持久化记录器（conn 有效时非 null）----
    private var dataWriteFile: DataWriteFile? = null

    // ---- 循环发送任务句柄 ----
    private var loopJob: Job? = null
    private var simulator: DeviceSimulator? = null
    private var simulatorListener: ((ByteArray) -> Unit)? = null
    private val leaseOwner = "串口页面-${UUID.randomUUID()}"
    private var leasedPort: String? = null
    private var appliedSettings: SerialConnectionSettings? = null

    init {
        // 启动全局串口监视器
        PortMonitor.start()

        // 观察串口列表并同步 UI；首次检测到串口时自动连接
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                availablePorts = ports
                val connectedPort = leasedPort
                if (connectedPort != null && connectedPort !in ports) {
                    closeCurrentPort("串口 $connectedPort 已断开")
                }
                if (autoConnectFirstPort && config.selectedPort.isEmpty() && ports.isNotEmpty()) {
                    config.selectedPort = ports[0]
                    syncPort()
                }
            }
        }

        // 注册串口数据回调（解耦：SerialComm 不再持有任何外部依赖）
        serialComm.addDataListener { data ->
            if (prefs.saveReceive) recorder()?.serialCommReceive(data)
        }

        // 观察串口原始数据流并推送到 UI 列表
        observeDataFlow()

        if (syncOnConfigChange) {
            observeConfigChanges()
        }
    }

    // ---- 私有监听方法 ----

    private fun observeDataFlow() {
        viewModelScope.launch {
            serialComm.dataFlow.collect { bytes ->
                rxCount += bytes.size
                if (prefs.showRealTime) {
                    val content = if (prefs.hexDisplay) bytes.toHexString() else String(bytes)
                    withContext(Dispatchers.Main) {
                        addLog(content, LogType.RX)
                    }
                }
            }
        }
    }

    private fun observeConfigChanges() {
        viewModelScope.launch {
            snapshotFlow { currentConnectionSettings() }
                .collectLatest { settings ->
                    if (isPortOpen && settings != appliedSettings) {
                        delay(150)
                        if (
                            isPortOpen &&
                            currentConnectionSettings() == settings &&
                            settings != appliedSettings
                        ) {
                            syncPort()
                        }
                    }
                }
        }
    }

    // ---- 业务操作 ----

    /** 打开 / 关闭串口的统一入口 */
    fun togglePort() {
        if (isPortOpen) {
            closeCurrentPort()
        } else {
            syncPort()
        }
    }

    /** 执行底层连接同步（参数变化时自动调用） */
    private fun syncPort() {
        val settings = currentConnectionSettings()
        val port = settings.port
        if (port.isEmpty()) return
        if (leasedPort != null && leasedPort != port) {
            closeCurrentPort()
        }
        if (!SerialPortLeaseRegistry.acquire(port, leaseOwner)) {
            val owner = SerialPortLeaseRegistry.ownerOf(port) ?: "其他页面"
            resetRecorder()
            isPortOpen = false
            appliedSettings = null
            addLog("$port 已被 $owner 占用", LogType.INFO)
            return
        }
        val success = serialComm.openPort(
            settings.port,
            settings.baudRate,
            settings.dataBits,
            settings.stopBitsIndex,
            settings.parityIndex,
            settings.flowControlIndex
        )
        isPortOpen = success
        if (success) {
            leasedPort = port
            appliedSettings = settings
            resetRecorder()
            addLog(connectionSettingsAppliedMessage(settings), LogType.INFO)
        } else {
            SerialPortLeaseRegistry.release(port, leaseOwner)
            leasedPort = null
            appliedSettings = null
            resetRecorder()
            val detail = serialComm.lastErrorMessage?.let { ": $it" }.orEmpty()
            addLog("同步失败: $port$detail", LogType.INFO)
        }
    }

    /** 发送字符串或 HEX 至串口 */
    fun sendData(data: String) {
        if (!isPortOpen || data.isBlank()) return
        val bytes = if (prefs.hexSend) {
            val result = HexInputParser.parse(data)
            if (!result.isValid) {
                addLog(result.error ?: "Invalid HEX input", LogType.INFO)
                return
            }
            result.bytes ?: byteArrayOf()
        } else {
            TextEscapeParser.decodeToByteArray(data)
        }
        val hexMode = prefs.hexSend
        viewModelScope.launch { sendBytes(data, bytes, hexMode) }
    }

    fun canSend(data: String): Boolean {
        if (!isPortOpen || data.isBlank()) return false
        return !prefs.hexSend || HexInputParser.parse(data).isValid
    }

    private suspend fun sendBytes(source: String, bytes: ByteArray, hexMode: Boolean) {
        if (bytes.isEmpty()) return
        val sent = withContext(Dispatchers.IO) { serialComm.write(bytes) }
        if (sent < 0) {
            closeCurrentPort("发送失败，连接已关闭")
            return
        }
        if (prefs.saveSend) recorder()?.serialCommSend(bytes)
        txCount += sent
        sendHistory.record(source)
        addLog(if (hexMode) bytes.toHexString() else source, LogType.TX)
    }

    fun previousHistory() {
        sendHistory.previous(inputText)?.let { inputText = it }
    }

    fun nextHistory() {
        sendHistory.next()?.let { inputText = it }
    }

    fun selectHistory(command: String) {
        inputText = command
        sendHistory.resetNavigation()
    }

    fun promoteHistoryToQuickSend(command: String) {
        val escaped = command
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        val existing = prefs.quickCommandsText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (escaped !in existing) {
            prefs.quickCommandsText = (existing + escaped).joinToString("\n")
        }
    }

    fun inputBytesOrNull(): ByteArray? {
        if (inputText.isBlank()) return null
        return if (prefs.hexSend) {
            HexInputParser.parse(inputText).bytes
        } else {
            TextEscapeParser.decodeToByteArray(inputText)
        }
    }

    fun checksum(algorithm: ChecksumAlgorithm): ByteArray? =
        inputBytesOrNull()?.let { ChecksumUtils.calculate(it, algorithm) }

    fun appendChecksum(algorithm: ChecksumAlgorithm): Boolean {
        val input = inputBytesOrNull() ?: return false
        val checksum = ChecksumUtils.calculate(input, algorithm)
        inputText = (input + checksum).joinToString(" ") { byte -> "%02X".format(byte.toUByte().toInt()) }
        prefs.hexSend = true
        sendHistory.resetNavigation()
        return true
    }

    /** 加载自动回复配置文件 */
    fun loadSimulator(file: File) {
        val validation = ConfigValidator.validateSimulator(file, prefs.currentLanguage)
        configIssues.clear()
        configIssues.addAll(validation.issues)
        validation.issues.forEach { issue ->
            val prefix = if (issue.severity == ConfigIssueSeverity.ERROR) "ERROR" else "WARN"
            addLog("$prefix: ${issue.message}", LogType.INFO)
        }
        if (!validation.isValid) {
            clearSimulator()
            simulatorConfigName = file.name
            isSimulating = false
            return
        }
        val newSimulator = DeviceSimulator.createDeviceSimulator(file)
        if (newSimulator != null) {
            clearSimulator()
            applySimulatorEndpointConfig(file)
            applySimulatorHighlightConfig(file)
            newSimulator.serialComm = serialComm
            newSimulator.onReply = { data, written ->
                viewModelScope.launch {
                    if (written > 0) {
                        if (prefs.saveSend) recorder()?.serialCommSend(data)
                        txCount += written
                        val content = if (prefs.hexDisplay) data.toHexString() else data.toUtf8String()
                        addLog(content, LogType.TX)
                    } else {
                        closeCurrentPort("自动回复发送失败，连接已关闭")
                    }
                }
            }
            val listener: (ByteArray) -> Unit = { data -> newSimulator.checkData(data) }
            serialComm.addDataListener(listener)
            simulator = newSimulator
            simulatorListener = listener
            isSimulating = true
            simulatorConfigName = file.name
            addLog("自动回复配置加载成功: ${file.name}", LogType.INFO)
            if (isPortOpen || readSimulatorAutoOpen(file)) {
                syncPort()
            }
        } else {
            clearSimulator()
            simulatorConfigName = ""
            addLog("自动回复配置解析失败", LogType.INFO)
            isSimulating = false
        }
    }

    /** 追加日志条目，最多保留 1000 条 */
    fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss").format(Date())
        if (logs.size >= 1000) logs.removeAt(0)
        logs.add(LogEntry(time, type, content, nextLogId++))
    }

    // ---- 循环发送 ----

    fun startLoopSend() {
        stopLoopSend()
        if (!prefs.isLoopIntervalValid) {
            prefs.loopSendEnabled = false
            addLog("循环发送间隔必须在 1 到 86400000 毫秒之间", LogType.INFO)
            return
        }
        if (prefs.loopSendEnabled && isPortOpen) {
            loopJob = viewModelScope.launch {
                while (isActive) {
                    val data = inputText
                    if (data.isNotBlank()) {
                        val hexMode = prefs.hexSend
                        val parseResult = if (hexMode) HexInputParser.parse(data) else null
                        if (parseResult != null && !parseResult.isValid) {
                            prefs.loopSendEnabled = false
                            addLog(
                                "循环发送已停止: ${parseResult.error ?: "Invalid HEX input"}",
                                LogType.INFO
                            )
                            break
                        }
                        val bytes = if (hexMode) {
                            parseResult?.bytes ?: byteArrayOf()
                        } else {
                            TextEscapeParser.decodeToByteArray(data)
                        }
                        if (bytes.isNotEmpty()) sendBytes(data, bytes, hexMode)
                    }
                    delay(prefs.loopInterval)
                }
            }
        }
    }

    fun stopLoopSend() {
        loopJob?.cancel()
        loopJob = null
    }

    // ---- 辅助操作 ----

    fun clearLogs() = logs.clear()
    fun clearTx() { txCount = 0 }
    fun clearRx() { rxCount = 0 }

    // ---- 资源回收 ----

    public override fun onCleared() {
        closeCurrentPort()
        serialComm.clearDataListeners()
        clearSimulator()
        stopLoopSend()
        logs.clear()
        txCount = 0
        rxCount = 0
        super.onCleared()
    }

    private fun closeCurrentPort(message: String = "连接已断开") {
        val wasConnected = isPortOpen || leasedPort != null
        serialComm.closePort()
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
        appliedSettings = null
        resetRecorder()
        isPortOpen = false
        stopLoopSend()
        if (wasConnected) addLog(message, LogType.INFO)
    }

    private fun recorder(): DataWriteFile? {
        val port = leasedPort ?: config.selectedPort.takeIf { it.isNotBlank() } ?: return null
        if (dataWriteFile == null) {
            dataWriteFile = DataWriteFile(port)
        }
        return dataWriteFile
    }

    private fun resetRecorder() {
        dataWriteFile?.close()
        dataWriteFile = null
    }

    private fun currentConnectionSettings(): SerialConnectionSettings {
        return SerialConnectionSettings(
            port = config.selectedPort,
            baudRate = config.baudRate,
            dataBits = config.dataBits,
            stopBitsIndex = config.stopBitsIndex,
            parityIndex = config.parityIndex,
            flowControlIndex = config.flowControlIndex
        )
    }

    private fun connectionSettingsAppliedMessage(settings: SerialConnectionSettings): String {
        val stopBits = listOf("1", "1.5", "2").getOrElse(settings.stopBitsIndex) { "1" }
        val parity = when (prefs.currentLanguage) {
            Language.ZH -> listOf("无", "奇校验", "偶校验", "标记校验", "空格校验")
            Language.EN -> listOf("none", "odd", "even", "mark", "space")
            Language.JA -> listOf("なし", "奇数", "偶数", "マーク", "スペース")
        }.getOrElse(settings.parityIndex) { "none" }
        val flowControl = when (settings.flowControlIndex) {
            1 -> "RTS/CTS"
            2 -> "DSR/DTR"
            3 -> "XON/XOFF"
            else -> when (prefs.currentLanguage) {
                Language.ZH -> "无"
                Language.EN -> "none"
                Language.JA -> "なし"
            }
        }
        return when (prefs.currentLanguage) {
            Language.ZH -> "连接参数已生效：${settings.port} · ${settings.baudRate} baud · 数据位 ${settings.dataBits} · 停止位 $stopBits · 校验 $parity · 流控 $flowControl"
            Language.EN -> "Connection settings applied: ${settings.port} · ${settings.baudRate} baud · ${settings.dataBits} data bits · $stopBits stop bits · parity $parity · flow control $flowControl"
            Language.JA -> "接続設定を適用しました：${settings.port} · ${settings.baudRate} baud · データビット ${settings.dataBits} · ストップビット $stopBits · パリティ $parity · フロー制御 $flowControl"
        }
    }

    private fun clearSimulator() {
        simulatorListener?.let { serialComm.removeDataListener(it) }
        simulatorListener = null
        simulator?.close()
        simulator = null
    }

    private fun applySimulatorEndpointConfig(file: File) {
        val endpoint = readSimulatorEndpoint(file) ?: return
        endpoint.optionalString("port")?.let { config.selectedPort = it }
        endpoint.optionalInt("baudRate")?.let { config.baudRate = it }
        endpoint.optionalInt("dataBits")?.let { config.dataBits = it }
        endpoint.optionalString("stopBits")?.let { config.stopBitsIndex = stopBitsToIndex(it) }
        endpoint.optionalString("parity")?.let { config.parityIndex = parityToIndex(it) }
        endpoint.optionalString("flowControl")?.let { config.flowControlIndex = flowControlToIndex(it) }
    }

    private fun applySimulatorHighlightConfig(file: File) {
        val simulatorConfig = readSimulatorConfig(file) ?: return
        if (!simulatorConfig.hasPath("highlight")) return
        val highlightConfig = simulatorConfig.getConfig("highlight")
        val keywords = highlightConfig.optionalStringList("keywords")
        if (keywords.isNotEmpty()) {
            prefs.highlight.setKeywords(keywords)
            prefs.highlight.enabled = highlightConfig.optionalBoolean("enabled") ?: true
        } else {
            highlightConfig.optionalBoolean("enabled")?.let { prefs.highlight.enabled = it }
        }
        highlightConfig.optionalBoolean("ignoreCase")?.let { prefs.highlight.ignoreCase = it }
    }

    private fun readSimulatorAutoOpen(file: File): Boolean {
        return readSimulatorEndpoint(file)?.optionalBoolean("autoOpen") == true
    }

    private fun readSimulatorEndpoint(file: File): Config? {
        val simulatorConfig = readSimulatorConfig(file) ?: return null
        return if (simulatorConfig.hasPath("endpoint")) simulatorConfig.getConfig("endpoint") else null
    }

    private fun readSimulatorConfig(file: File): Config? {
        if (!file.extension.equals("conf", ignoreCase = true) && !file.extension.equals("hocon", ignoreCase = true)) {
            return null
        }
        return runCatching {
            val parsed = ConfigFactory.parseFile(file).resolve()
            if (parsed.hasPath("simulator")) parsed.getConfig("simulator") else parsed
        }.getOrNull()
    }

    private fun stopBitsToIndex(value: String): Int {
        return when (value.trim().lowercase()) {
            "1.5", "one_point_five" -> 1
            "2", "two" -> 2
            else -> 0
        }
    }

    private fun parityToIndex(value: String): Int {
        return when (value.trim().lowercase()) {
            "odd" -> 1
            "even" -> 2
            "mark" -> 3
            "space" -> 4
            else -> 0
        }
    }

    private fun flowControlToIndex(value: String): Int {
        return when (value.trim().lowercase()) {
            "rts/cts", "rts_cts" -> 1
            "dsr/dtr", "dsr_dtr" -> 2
            "xon/xoff", "xon_xoff" -> 3
            else -> 0
        }
    }

    private fun Config.optionalString(path: String): String? {
        return if (hasPath(path)) getValue(path).unwrapped().toString() else null
    }

    private fun Config.optionalInt(path: String): Int? {
        return if (hasPath(path)) getValue(path).unwrapped().toString().toIntOrNull() else null
    }

    private fun Config.optionalBoolean(path: String): Boolean? {
        return if (hasPath(path)) getValue(path).unwrapped().toString().toBooleanStrictOrNull() else null
    }

    private fun Config.optionalStringList(path: String): List<String> {
        if (!hasPath(path)) return emptyList()
        return runCatching { getStringList(path) }.getOrElse {
            optionalString(path)
                ?.split(",", ";", "\n", "\r")
                ?.map { item -> item.trim() }
                ?.filter { item -> item.isNotEmpty() }
                ?: emptyList()
        }
    }
}
