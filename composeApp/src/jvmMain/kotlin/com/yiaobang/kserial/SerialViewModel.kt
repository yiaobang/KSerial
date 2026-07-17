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
import com.yiaobang.kserial.model.AppPreferences
import com.yiaobang.kserial.model.DataWriteFile
import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * 串口视图模型类
 * 职责：业务逻辑协调层，连接 UI 层与底层通信层
 * 串口参数由 [config] 管理，UI 偏好由 [prefs] 管理
 */
class SerialViewModel(
    private val autoConnectFirstPort: Boolean = true,
    private val syncOnConfigChange: Boolean = true
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

    // ---- 发送区文本（提升到 VM 供循环发送读取）----
    var inputText by mutableStateOf("")

    // ---- 统计计数 ----
    var txCount by mutableLongStateOf(0L)
    var rxCount by mutableLongStateOf(0L)

    // ---- 日志列表 ----
    val logs = androidx.compose.runtime.mutableStateListOf<LogEntry>()

    // ---- 持久化记录器（conn 有效时非 null）----
    private var dataWriteFile: DataWriteFile? = null

    // ---- 循环发送任务句柄 ----
    private var loopJob: Job? = null
    private var simulator: DeviceSimulator? = null
    private var simulatorListener: ((ByteArray) -> Unit)? = null
    private val leaseOwner = "串口页面-${UUID.randomUUID()}"
    private var leasedPort: String? = null

    init {
        // 启动全局串口监视器
        PortMonitor.start()

        // 观察串口列表并同步 UI；首次检测到串口时自动连接
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                availablePorts = ports
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
        // 分两组监听，避免 Triple 嵌套过深
        viewModelScope.launch {
            snapshotFlow { Triple(config.selectedPort, config.baudRate, config.dataBits) }
                .collect { syncPort() }
        }
        viewModelScope.launch {
            snapshotFlow { Triple(config.stopBitsIndex, config.parityIndex, config.flowControlIndex) }
                .collect { syncPort() }
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
        val port = config.selectedPort
        if (port.isEmpty()) return
        if (leasedPort != null && leasedPort != port) {
            closeCurrentPort()
        }
        if (!SerialPortLeaseRegistry.acquire(port, leaseOwner)) {
            val owner = SerialPortLeaseRegistry.ownerOf(port) ?: "其他页面"
            resetRecorder()
            isPortOpen = false
            addLog("$port 已被 $owner 占用", LogType.INFO)
            return
        }
        val success = serialComm.openPort(
            port, config.baudRate, config.dataBits,
            config.stopBitsIndex, config.parityIndex, config.flowControlIndex
        )
        isPortOpen = success
        if (success) {
            leasedPort = port
            resetRecorder()
            addLog("串口 $port 已就绪", LogType.INFO)
        } else {
            SerialPortLeaseRegistry.release(port, leaseOwner)
            leasedPort = null
            resetRecorder()
            addLog("同步失败: $port", LogType.INFO)
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
            data.toByteArray()
        }
        val hexMode = prefs.hexSend
        viewModelScope.launch { sendBytes(data, bytes, hexMode) }
    }

    private suspend fun sendBytes(source: String, bytes: ByteArray, hexMode: Boolean) {
        if (bytes.isEmpty()) return
        val sent = withContext(Dispatchers.IO) { serialComm.write(bytes) }
        if (sent < 0) {
            addLog("发送失败: 串口未连接或写入异常", LogType.INFO)
            return
        }
        if (prefs.saveSend) recorder()?.serialCommSend(bytes)
        txCount += sent
        addLog(if (hexMode) bytes.toHexString() else source, LogType.TX)
    }

    /** 加载自动回复配置文件 */
    fun loadSimulator(file: File) {
        val newSimulator = DeviceSimulator.createDeviceSimulator(file)
        if (newSimulator != null) {
            clearSimulator()
            applySimulatorEndpointConfig(file)
            applySimulatorHighlightConfig(file)
            validateSimulatorConfig(file).forEach { addLog(it, LogType.INFO) }
            newSimulator.serialComm = serialComm
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
        logs.add(LogEntry(time, type, content))
    }

    // ---- 循环发送 ----

    fun startLoopSend() {
        stopLoopSend()
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
                            data.toByteArray()
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

    private fun closeCurrentPort() {
        serialComm.closePort()
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
        resetRecorder()
        isPortOpen = false
        stopLoopSend()
        addLog("连接已断开", LogType.INFO)
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

    private fun validateSimulatorConfig(file: File): List<String> {
        val simulatorConfig = readSimulatorConfig(file) ?: return emptyList()
        val warnings = mutableListOf<String>()
        val endpoint = if (simulatorConfig.hasPath("endpoint")) simulatorConfig.getConfig("endpoint") else null
        if (endpoint == null) {
            warnings += "配置警告: simulator.endpoint 未声明，串口参数将沿用当前页面状态"
        } else {
            if (endpoint.optionalString("port").isNullOrBlank()) {
                warnings += "配置警告: simulator.endpoint.port 为空"
            }
            if (endpoint.optionalInt("baudRate") == null) {
                warnings += "配置警告: simulator.endpoint.baudRate 缺失或非法"
            }
        }
        if (!simulatorConfig.hasPath("delimiter") && !simulatorConfig.hasPath("packSize")) {
            warnings += "配置警告: delimiter 和 packSize 至少建议提供一个"
        }
        return warnings
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
