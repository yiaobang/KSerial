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
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.i18n.RuntimeMessages
import com.yiaobang.kserial.model.ConfigIssue
import com.yiaobang.kserial.model.ConfigIssueSeverity
import com.yiaobang.kserial.model.ConfigValidator
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogFilterConfig
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransportEvent
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** 未提供显式 routes 时使用的动态桥接拓扑。 */
enum class GatewayMode(val label: String) {
    OneToOne("一对一"),
    OneToMany("一对多"),
    ManyToMany("多对多")
}

/** 单个桥接端点的串口配置、连接状态、计数器和独占租约。 */
class GatewayEndpoint(
    val index: Int,
    val id: String,
    val name: String,
    val serialComm: SerialTransport = SerialComm()
) {
    var selectedPort by mutableStateOf("")
    var baudRate by mutableIntStateOf(9600)
    var dataBits by mutableIntStateOf(8)
    var stopBitsIndex by mutableIntStateOf(0)
    var parityIndex by mutableIntStateOf(0)
    var flowControlIndex by mutableIntStateOf(0)
    var isOpen by mutableStateOf(false)
    var txCount by mutableLongStateOf(0L)
    var rxCount by mutableLongStateOf(0L)
    val leaseOwner = "串口桥接:$name:${UUID.randomUUID()}"
    var leasedPort: String? = null
    internal val receivedTotal = AtomicLong()
    internal val droppedBytes = AtomicLong()
    internal var rxBaseline = 0L
    internal val receiveQueue = Channel<ByteArray>(
        capacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { droppedBytes.addAndGet(it.size.toLong()) }
    )
    internal var receiveJob: Job? = null
    @Volatile internal var closingIntentionally = false
}

/** 配置文件中的有向路由；一个来源集合可以映射到多个目标。 */
class GatewayRoute(
    val name: String,
    val sourceIds: Set<String>,
    val targetIds: Set<String>,
    val enabled: Boolean
)

/**
 * 串口桥接业务状态：管理多个物理端点、解析路由并转发原始字节。
 * UI 不直接持有 SerialComm，页面销毁时可在这里统一释放所有端口。
 */
class GatewayViewModel : ViewModel() {
    val endpoints = mutableStateListOf<GatewayEndpoint>()
    val routes = mutableStateListOf<GatewayRoute>()
    val logs = mutableStateListOf<LogEntry>()
    val highlight = KeywordHighlightConfig()
    val logFilter = LogFilterConfig()
    val configIssues = mutableStateListOf<ConfigIssue>()

    var mode by mutableStateOf(GatewayMode.OneToOne)
    var sourceIndex by mutableIntStateOf(0)
    var targetIndex by mutableIntStateOf(1)
    var hexDisplay by mutableStateOf(true)
    var loadedConfigName by mutableStateOf("")
    var language by mutableStateOf(Language.fromLocale())
    private var nextLogId = 1L

    init {
        PortMonitor.start()
        resetDefaultEndpoints()
        // 系统端口消失时立即释放对应租约，避免 UI 继续显示已连接。
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                endpoints
                    .filter { endpoint ->
                        val port = endpoint.leasedPort
                        endpoint.isOpen && port != null && port !in ports
                    }
                    .forEach { endpoint ->
                        val port = endpoint.leasedPort ?: return@forEach
                        closeEndpoint(endpoint, RuntimeMessages.portDisconnected(language, port))
                    }
            }
        }
    }

    fun loadConfig(file: File) {
        // 先完整校验；存在 ERROR 时展示问题，但不应用不完整的端点和路由。
        val validation = ConfigValidator.validateGateway(file, language)
        configIssues.clear()
        configIssues.addAll(validation.issues)
        validation.issues.forEach { issue ->
            val prefix = if (issue.severity == ConfigIssueSeverity.ERROR) "ERROR" else "WARN"
            addLog("$prefix: ${issue.message}", LogType.INFO)
        }
        loadedConfigName = file.name
        if (!validation.isValid) {
            // 配置是桥接运行的唯一来源；无效文件不能继续暴露上一份配置的“运行”入口。
            replaceEndpoints(emptyList())
            routes.clear()
            return
        }
        runCatching {
            val config = validation.config ?: error("missing gateway configuration")
            val endpointConfigs = config.getConfigList("endpoints")
            val loadedEndpoints = endpointConfigs.mapIndexed { index, endpointConfig ->
                GatewayEndpoint(
                    index = index,
                    id = endpointConfig.optionalString("id") ?: "endpoint$index",
                    name = endpointConfig.optionalString("name") ?: endpointConfig.optionalString("id")
                    ?: RuntimeMessages.endpointDefaultName(language, index + 1)
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
            // 一次性替换端点，防止新旧监听器或租约交叉残留。
            replaceEndpoints(loadedEndpoints)
            routes.clear()
            routes.addAll(loadedRoutes)
            sourceIndex = 0
            targetIndex = if (endpoints.size > 1) 1 else 0
            addLog(RuntimeMessages.gatewayLoaded(language, file.name), LogType.INFO)
        }.onFailure {
            addLog(RuntimeMessages.gatewayLoadFailed(language, it.message ?: "unknown error"), LogType.INFO)
        }
    }

    fun openEndpoint(endpoint: GatewayEndpoint) {
        val port = endpoint.selectedPort
        if (port.isBlank()) {
            addLog(RuntimeMessages.endpointNoPort(language, endpoint.name), LogType.INFO)
            return
        }
        val duplicatedInConfig = endpoints.any { it !== endpoint && it.selectedPort == port }
        if (duplicatedInConfig) {
            addLog(RuntimeMessages.endpointDuplicate(language, endpoint.name, port), LogType.INFO)
            return
        }
        // 租约先于底层 openPort 获取，阻止其他页面并发打开同一串口。
        if (!SerialPortLeaseRegistry.acquire(port, endpoint.leaseOwner)) {
            val owner = RuntimeMessages.otherPage(language)
            addLog(RuntimeMessages.portOccupied(language, port, owner), LogType.INFO)
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
        addLog(RuntimeMessages.endpointOpened(language, endpoint.name, port, success), LogType.INFO)
    }

    fun closeEndpoint(endpoint: GatewayEndpoint, message: String? = null) {
        endpoint.closingIntentionally = true
        try {
            endpoint.serialComm.closePort()
        } finally {
            endpoint.closingIntentionally = false
        }
        endpoint.leasedPort?.let { SerialPortLeaseRegistry.release(it, endpoint.leaseOwner) }
        endpoint.leasedPort = null
        endpoint.isOpen = false
        addLog(message ?: RuntimeMessages.endpointClosed(language, endpoint.name), LogType.INFO)
    }

    fun toggleEndpoint(endpoint: GatewayEndpoint) {
        if (endpoint.isOpen) closeEndpoint(endpoint) else openEndpoint(endpoint)
    }

    fun openAllEndpoints() {
        endpoints.filterNot { it.isOpen }.forEach(::openEndpoint)
    }

    fun closeAllEndpoints() {
        endpoints.filter { it.isOpen }.forEach { closeEndpoint(it) }
    }

    fun clearLogs() {
        logs.clear()
    }

    fun clearCounters() {
        endpoints.forEach {
            it.txCount = 0
            it.rxCount = 0
            it.rxBaseline = it.receivedTotal.get()
        }
    }

    private fun resetDefaultEndpoints() {
        replaceEndpoints(emptyList())
    }

    private fun replaceEndpoints(newEndpoints: List<GatewayEndpoint>) {
        // 旧端点先解除监听和租约，随后才给新端点注册数据回调。
        endpoints.forEach {
            it.receiveJob?.cancel()
            it.serialComm.closePort()
            it.serialComm.clearDataListeners()
            it.serialComm.clearTransportListeners()
            it.receiveQueue.close()
            it.leasedPort?.let { port -> SerialPortLeaseRegistry.release(port, it.leaseOwner) }
            it.leasedPort = null
        }
        endpoints.clear()
        endpoints.addAll(newEndpoints)
        endpoints.forEach { endpoint ->
            endpoint.serialComm.addDataListener { data ->
                endpoint.receivedTotal.addAndGet(data.size.toLong())
                endpoint.receiveQueue.trySend(data)
            }
            endpoint.serialComm.addTransportListener { event ->
                viewModelScope.launch {
                    if (!endpoint.closingIntentionally && endpoint.isOpen) {
                        when (event) {
                            SerialTransportEvent.Disconnected -> closeEndpoint(
                                endpoint,
                                RuntimeMessages.portDisconnected(language, endpoint.selectedPort)
                            )
                            is SerialTransportEvent.Error -> addLog(RuntimeMessages.lineEvent(language, event.detail), LogType.INFO)
                        }
                    }
                }
            }
            endpoint.receiveJob = viewModelScope.launch {
                while (isActive) {
                    val data = endpoint.receiveQueue.receiveCatching().getOrNull() ?: break
                    onEndpointData(endpoint, data)
                }
            }
        }
    }

    internal suspend fun onEndpointData(source: GatewayEndpoint, data: ByteArray) {
        // 每个来源端点只有一个消费协程，保证该来源的转发顺序稳定。
        source.rxCount = (source.receivedTotal.get() - source.rxBaseline).coerceAtLeast(0L)
        val dropped = source.droppedBytes.getAndSet(0L)
        if (dropped > 0) addLog(RuntimeMessages.gatewayDropped(language, source.name, dropped), LogType.INFO)

        val targets = resolveTargets(source).filter { it.isOpen }
        targets.forEach { target ->
            val written = withContext(Dispatchers.IO) { target.serialComm.write(data) }
            if (written > 0) {
                target.txCount += written
                val sentData = data.copyOf(written.coerceAtMost(data.size))
                addLog(RuntimeMessages.forwarded(language, source.name, target.name, formatData(sentData)), LogType.TX)
            } else {
                closeEndpoint(target, RuntimeMessages.forwardingFailed(language, source.name, target.name))
            }
        }
    }

    private fun resolveTargets(source: GatewayEndpoint): List<GatewayEndpoint> {
        // 显式 routes 优先于页面动态模式，禁用路由和来源自身在这里过滤。
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
        if (logs.size >= 1000) logs.removeAt(0)
        logs.add(LogEntry(time, type, content, nextLogId++))
    }

    fun reportInfo(content: String) {
        addLog(content, LogType.INFO)
    }

    private fun applyHighlightConfig(config: Config) {
        val keywords = config.optionalStringList("keywords")
        highlight.setKeywords(keywords)
        highlight.enabled = config.optionalBoolean("enabled") ?: keywords.isNotEmpty()
        highlight.ignoreCase = config.optionalBoolean("ignoreCase") ?: true
    }

    public override fun onCleared() {
        // 桥接可能同时占用多个端口，页面销毁必须逐一关闭并释放租约。
        endpoints.forEach {
            it.receiveJob?.cancel()
            it.serialComm.closePort()
            it.serialComm.clearDataListeners()
            it.serialComm.clearTransportListeners()
            it.receiveQueue.close()
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
