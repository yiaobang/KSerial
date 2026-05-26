package com.yiaobang.kserial.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogFilterConfig
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

enum class GatewayMode(val label: String) {
    OneToOne("一对一"),
    OneToMany("一对多"),
    ManyToMany("多对多")
}

class GatewayEndpoint(
    val index: Int,
    val id: String,
    val name: String
) {
    val serialComm = SerialComm()
    var selectedPort by mutableStateOf("")
    var baudRate by mutableIntStateOf(9600)
    var dataBits by mutableIntStateOf(8)
    var stopBitsIndex by mutableIntStateOf(0)
    var parityIndex by mutableIntStateOf(0)
    var flowControlIndex by mutableIntStateOf(0)
    var isOpen by mutableStateOf(false)
    var txCount by mutableLongStateOf(0L)
    var rxCount by mutableLongStateOf(0L)
    val leaseOwner: String get() = "串口网关:$name"
    var leasedPort: String? = null
}

class GatewayRoute(
    val name: String,
    val sourceIds: Set<String>,
    val targetIds: Set<String>,
    val enabled: Boolean
)

class GatewayViewModel : ViewModel() {
    val endpoints = mutableStateListOf<GatewayEndpoint>()
    val routes = mutableStateListOf<GatewayRoute>()
    val logs = mutableStateListOf<LogEntry>()
    val highlight = KeywordHighlightConfig()
    val logFilter = LogFilterConfig()

    var mode by mutableStateOf(GatewayMode.OneToOne)
    var sourceIndex by mutableIntStateOf(0)
    var targetIndex by mutableIntStateOf(1)
    var hexDisplay by mutableStateOf(true)
    var loadedConfigName by mutableStateOf("")

    init {
        resetDefaultEndpoints()
    }

    fun loadConfig(file: File) {
        runCatching {
            val parsed = ConfigFactory.parseFile(file).resolve()
            val config = if (parsed.hasPath("gateway")) parsed.getConfig("gateway") else parsed
            val endpointConfigs = config.getConfigList("endpoints")
            val loadedEndpoints = endpointConfigs.mapIndexed { index, endpointConfig ->
                GatewayEndpoint(
                    index = index,
                    id = endpointConfig.optionalString("id") ?: "endpoint$index",
                    name = endpointConfig.optionalString("name") ?: endpointConfig.optionalString("id") ?: "端点${index + 1}"
                ).apply {
                    selectedPort = endpointConfig.optionalString("port") ?: ""
                    baudRate = endpointConfig.optionalInt("baudRate") ?: 9600
                    dataBits = endpointConfig.optionalInt("dataBits") ?: 8
                    stopBitsIndex = stopBitsToIndex(endpointConfig.optionalString("stopBits") ?: "1")
                    parityIndex = parityToIndex(endpointConfig.optionalString("parity") ?: "none")
                    flowControlIndex = flowControlToIndex(endpointConfig.optionalString("flowControl") ?: "none")
                }
            }
            val loadedRoutes = if (config.hasPath("routes")) {
                config.getConfigList("routes").mapIndexed { index, routeConfig ->
                    GatewayRoute(
                        name = routeConfig.optionalString("name") ?: "route${index + 1}",
                        sourceIds = routeConfig.optionalStringList("sources").toSet(),
                        targetIds = routeConfig.optionalStringList("targets").toSet(),
                        enabled = routeConfig.optionalBoolean("enabled") ?: true
                    )
                }
            } else {
                emptyList()
            }
            if (config.hasPath("highlight")) {
                applyHighlightConfig(config.getConfig("highlight"))
            }
            val warnings = validateConfig(loadedEndpoints, loadedRoutes)

            replaceEndpoints(loadedEndpoints)
            routes.clear()
            routes.addAll(loadedRoutes)
            sourceIndex = 0
            targetIndex = if (endpoints.size > 1) 1 else 0
            loadedConfigName = file.name
            addLog("已加载网关配置: ${file.name}", LogType.INFO)
            warnings.forEach { addLog(it, LogType.INFO) }

            endpointConfigs.forEachIndexed { index, endpointConfig ->
                if (endpointConfig.optionalBoolean("autoOpen") == true) {
                    endpoints.getOrNull(index)?.let { openEndpoint(it) }
                }
            }
        }.onFailure {
            addLog("网关配置加载失败: ${it.message ?: "unknown error"}", LogType.INFO)
        }
    }

    fun openEndpoint(endpoint: GatewayEndpoint) {
        val port = endpoint.selectedPort
        if (port.isBlank()) {
            addLog("${endpoint.name}: 未选择串口", LogType.INFO)
            return
        }
        val duplicatedInConfig = endpoints.any { it !== endpoint && it.selectedPort == port }
        if (duplicatedInConfig) {
            addLog("${endpoint.name}: $port 在网关配置中重复", LogType.INFO)
            return
        }
        if (!SerialPortLeaseRegistry.acquire(port, endpoint.leaseOwner)) {
            val owner = SerialPortLeaseRegistry.ownerOf(port) ?: "其他页面"
            addLog("${endpoint.name}: $port 已被 $owner 占用", LogType.INFO)
            return
        }

        val success = endpoint.serialComm.openPort(
            portName = port,
            baudRate = endpoint.baudRate,
            dataBits = endpoint.dataBits,
            stopBitsIndex = endpoint.stopBitsIndex,
            parityIndex = endpoint.parityIndex,
            flowControlIndex = endpoint.flowControlIndex
        )
        endpoint.isOpen = success
        if (success) {
            endpoint.leasedPort = port
        } else {
            SerialPortLeaseRegistry.release(port, endpoint.leaseOwner)
            endpoint.leasedPort = null
        }
        addLog(if (success) "${endpoint.name}: $port 已打开" else "${endpoint.name}: $port 打开失败", LogType.INFO)
    }

    fun closeEndpoint(endpoint: GatewayEndpoint) {
        endpoint.serialComm.closePort()
        endpoint.leasedPort?.let { SerialPortLeaseRegistry.release(it, endpoint.leaseOwner) }
        endpoint.leasedPort = null
        endpoint.isOpen = false
        addLog("${endpoint.name}: 已关闭", LogType.INFO)
    }

    fun toggleEndpoint(endpoint: GatewayEndpoint) {
        if (endpoint.isOpen) closeEndpoint(endpoint) else openEndpoint(endpoint)
    }

    fun clearLogs() {
        logs.clear()
    }

    fun clearCounters() {
        endpoints.forEach {
            it.txCount = 0
            it.rxCount = 0
        }
    }

    private fun resetDefaultEndpoints() {
        replaceEndpoints(emptyList())
    }

    private fun replaceEndpoints(newEndpoints: List<GatewayEndpoint>) {
        endpoints.forEach {
            it.serialComm.closePort()
            it.serialComm.clearDataListeners()
            it.leasedPort?.let { port -> SerialPortLeaseRegistry.release(port, it.leaseOwner) }
            it.leasedPort = null
        }
        endpoints.clear()
        endpoints.addAll(newEndpoints)
        endpoints.forEach { endpoint ->
            endpoint.serialComm.addDataListener { data -> onEndpointData(endpoint, data) }
        }
    }

    private fun onEndpointData(source: GatewayEndpoint, data: ByteArray) {
        viewModelScope.launch(Dispatchers.Main) {
            source.rxCount += data.size
            addLog("${source.name} RX ${formatData(data)}", LogType.RX)

            val targets = resolveTargets(source).filter { it.isOpen }
            targets.forEach { target ->
                val written = withContext(Dispatchers.IO) { target.serialComm.write(data) }
                if (written > 0) {
                    target.txCount += written
                    addLog("${source.name} -> ${target.name} $written bytes", LogType.TX)
                } else {
                    addLog("${source.name} -> ${target.name} 转发失败", LogType.INFO)
                }
            }
        }
    }

    private fun resolveTargets(source: GatewayEndpoint): List<GatewayEndpoint> {
        if (routes.isNotEmpty()) {
            val targetIds = routes
                .filter { it.enabled && source.id in it.sourceIds }
                .flatMap { it.targetIds }
                .toSet()
            return endpoints.filter { it.id in targetIds && it !== source }
        }
        return when (mode) {
            GatewayMode.OneToOne -> {
                if (source.index != sourceIndex) emptyList()
                else endpoints.filter { it.index == targetIndex && it !== source }
            }
            GatewayMode.OneToMany -> {
                if (source.index != sourceIndex) emptyList()
                else endpoints.filter { it !== source }
            }
            GatewayMode.ManyToMany -> endpoints.filter { it !== source }
        }
    }

    private fun formatData(data: ByteArray): String {
        return if (hexDisplay) data.toHexString() else String(data)
    }

    private fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss").format(Date())
        if (logs.size > 1000) logs.removeAt(0)
        logs.add(LogEntry(time, type, content))
    }

    private fun validateConfig(
        loadedEndpoints: List<GatewayEndpoint>,
        loadedRoutes: List<GatewayRoute>
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (loadedEndpoints.isEmpty()) {
            warnings += "配置警告: gateway.endpoints 为空"
        }
        loadedEndpoints
            .groupBy { it.id }
            .filter { it.value.size > 1 }
            .keys
            .forEach { warnings += "配置警告: endpoint id 重复: $it" }
        loadedEndpoints
            .filter { it.selectedPort.isBlank() }
            .forEach { warnings += "配置警告: ${it.name} 未配置 port" }
        loadedEndpoints
            .filter { it.selectedPort.isNotBlank() }
            .groupBy { it.selectedPort }
            .filter { it.value.size > 1 }
            .forEach { (port, endpoints) ->
                warnings += "配置警告: $port 被多个端点使用: ${endpoints.joinToString { it.name }}"
            }

        val endpointIds = loadedEndpoints.map { it.id }.toSet()
        loadedRoutes.forEach { route ->
            val missingSources = route.sourceIds - endpointIds
            val missingTargets = route.targetIds - endpointIds
            if (missingSources.isNotEmpty()) {
                warnings += "配置警告: ${route.name} sources 不存在: ${missingSources.joinToString()}"
            }
            if (missingTargets.isNotEmpty()) {
                warnings += "配置警告: ${route.name} targets 不存在: ${missingTargets.joinToString()}"
            }
        }
        return warnings
    }

    private fun applyHighlightConfig(config: Config) {
        val keywords = config.optionalStringList("keywords")
        if (keywords.isNotEmpty()) {
            highlight.setKeywords(keywords)
            highlight.enabled = config.optionalBoolean("enabled") ?: true
        } else {
            config.optionalBoolean("enabled")?.let { highlight.enabled = it }
        }
        config.optionalBoolean("ignoreCase")?.let { highlight.ignoreCase = it }
    }

    public override fun onCleared() {
        endpoints.forEach {
            it.serialComm.closePort()
            it.serialComm.clearDataListeners()
            it.leasedPort?.let { port -> SerialPortLeaseRegistry.release(port, it.leaseOwner) }
            it.leasedPort = null
        }
        logs.clear()
        super.onCleared()
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
