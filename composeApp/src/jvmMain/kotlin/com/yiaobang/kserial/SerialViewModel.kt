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
import com.yiaobang.kserial.i18n.RuntimeMessages
import com.yiaobang.kserial.model.AppPreferences
import com.yiaobang.kserial.model.ConfigIssue
import com.yiaobang.kserial.model.ConfigIssueSeverity
import com.yiaobang.kserial.model.ConfigValidator
import com.yiaobang.kserial.model.DataWriteFile
import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.ReceiveDisplayMode
import com.yiaobang.kserial.model.ReceiveFrameAssembler
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.model.SerialConnectionState
import com.yiaobang.kserial.model.SerialLineSignals
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.model.SendHistoryState
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.serial.SerialPortInfo
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransportEvent
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.TextEscapeParser
import com.yiaobang.kserial.utils.ChecksumAlgorithm
import com.yiaobang.kserial.utils.ChecksumUtils
import com.yiaobang.kserial.utils.toHexString
import com.yiaobang.kserial.utils.toUtf8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

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
    val sendHistory: SendHistoryState = SendHistoryState(),
    val serialComm: SerialTransport = SerialComm()
) : ViewModel() {

    // ---- 状态持有者（职责分离）----
    /** 串口连接参数 */
    val config = SerialPortConfig()

    /** UI 显示偏好 */
    val prefs = AppPreferences()

    // ---- 串口列表 ----
    var availablePorts by mutableStateOf(emptyList<String>())
    var availablePortDetails by mutableStateOf(emptyList<SerialPortInfo>())

    // ---- 连接状态 ----
    var isPortOpen by mutableStateOf(false)
    var connectionState by mutableStateOf(SerialConnectionState.DISCONNECTED)
        private set
    var connectionDetail by mutableStateOf("")
        private set
    var lineSignals by mutableStateOf(SerialLineSignals())
        private set
    var dtrEnabled by mutableStateOf(false)
        private set
    var rtsEnabled by mutableStateOf(false)
        private set
    var breakEnabled by mutableStateOf(false)
        private set
    val isConnectionActive: Boolean
        get() = isPortOpen || connectionState == SerialConnectionState.CONNECTING ||
            connectionState == SerialConnectionState.RECONNECTING

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
    private var reconnectJob: Job? = null
    private var lineSignalJob: Job? = null
    private var simulator: DeviceSimulator? = null
    private var simulatorListener: ((ByteArray) -> Unit)? = null
    private val leaseOwner = "串口页面-${UUID.randomUUID()}"
    private var leasedPort: String? = null
    private var appliedSettings: SerialConnectionSettings? = null
    private var desiredConnected = false
    @Volatile
    private var closingIntentionally = false

    // 驱动线程只负责把不可变字节块放入有界队列；UI 协程按时间窗口批量消费。
    private val receivedByteTotal = AtomicLong()
    private val droppedDisplayBytes = AtomicLong()
    private val receiveQueue = Channel<ByteArray>(
        capacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { droppedDisplayBytes.addAndGet(it.size.toLong()) }
    )
    private val receiveAssembler = ReceiveFrameAssembler()
    private var rxCounterBaseline = 0L
    private var receiveConfigWarningSignature: String? = null

    private val transportListener: (SerialTransportEvent) -> Unit = { event ->
        viewModelScope.launch {
            if (!closingIntentionally && (isPortOpen || leasedPort != null)) {
                when (event) {
                    SerialTransportEvent.Disconnected -> handleUnexpectedDisconnect(RuntimeMessages.disconnected(prefs.currentLanguage))
                    is SerialTransportEvent.Error -> handleTransportError(event.detail)
                }
            }
        }
    }

    init {
        // 启动全局串口监视器
        PortMonitor.start()

        // 观察串口列表并同步 UI；首次检测到串口时自动连接
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                availablePorts = ports
                val connectedPort = leasedPort
                if (connectedPort != null && connectedPort !in ports) {
                    handleUnexpectedDisconnect(RuntimeMessages.portDisconnected(prefs.currentLanguage, connectedPort))
                }
                if (autoConnectFirstPort && config.selectedPort.isEmpty() && ports.isNotEmpty()) {
                    config.selectedPort = ports[0]
                    desiredConnected = true
                    syncPort()
                }
            }
        }

        viewModelScope.launch {
            PortMonitor.portDetails.collect { availablePortDetails = it }
        }

        // 接收计数在驱动线程累计，因此即使显示队列过载也不会少算物理接收字节。
        serialComm.addDataListener { data ->
            receivedByteTotal.addAndGet(data.size.toLong())
            receiveQueue.trySend(data)
            if (prefs.saveReceive) recorder()?.serialCommReceive(data)
        }
        serialComm.addTransportListener(transportListener)

        // 观察串口原始数据流并推送到 UI 列表
        observeDataFlow()

        if (syncOnConfigChange) {
            observeConfigChanges()
        }
    }

    // ---- 私有监听方法 ----

    private fun observeDataFlow() {
        viewModelScope.launch {
            while (isActive) {
                val first = receiveQueue.receiveCatching().getOrNull() ?: break
                val chunks = collectReceiveBatch(first)
                rxCount = (receivedByteTotal.get() - rxCounterBaseline).coerceAtLeast(0L)

                val dropped = droppedDisplayBytes.getAndSet(0L)
                if (dropped > 0) addLog(RuntimeMessages.receiveDropped(prefs.currentLanguage, dropped), LogType.INFO)

                if (!prefs.showRealTime) {
                    receiveAssembler.reset()
                    continue
                }

                val result = receiveAssembler.append(
                    chunks = chunks,
                    mode = prefs.receiveDisplayMode,
                    delimiterText = prefs.receiveDelimiterText,
                    frameSize = prefs.receiveFrameSize
                )
                if (result.invalidConfiguration) {
                    val signature = "${prefs.receiveDisplayMode}|${prefs.receiveDelimiterText}|${prefs.receiveFrameSizeText}"
                    if (receiveConfigWarningSignature != signature) {
                        receiveConfigWarningSignature = signature
                        addLog(RuntimeMessages.invalidReceiveFraming(prefs.currentLanguage), LogType.INFO)
                    }
                } else {
                    receiveConfigWarningSignature = null
                }
                if (result.droppedRemainderBytes > 0) {
                    addLog(RuntimeMessages.receiveRemainderDropped(prefs.currentLanguage, result.droppedRemainderBytes), LogType.INFO)
                }
                result.frames.forEach { bytes ->
                    val content = if (prefs.hexDisplay) bytes.toHexString() else String(bytes)
                    addLog(content, LogType.RX)
                }
            }
        }
    }

    private suspend fun collectReceiveBatch(first: ByteArray): List<ByteArray> {
        if (prefs.receiveDisplayMode == ReceiveDisplayMode.RAW_CHUNKS) return listOf(first)
        val chunks = mutableListOf(first)
        var byteCount = first.size
        val deadline = System.nanoTime() + prefs.receiveWindowMs * 1_000_000L
        while (byteCount < 65_536) {
            val remainingMillis = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
            if (remainingMillis <= 0L) break
            val next = withTimeoutOrNull(remainingMillis) { receiveQueue.receive() } ?: break
            chunks += next
            byteCount += next.size
        }
        return chunks
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
                            if (!syncPort() && desiredConnected) ensureReconnectLoop()
                        }
                    }
                }
        }
    }

    // ---- 业务操作 ----

    /** 打开 / 关闭串口的统一入口 */
    fun togglePort() {
        if (isConnectionActive) {
            desiredConnected = false
            reconnectJob?.cancel()
            reconnectJob = null
            closeCurrentPort()
        } else {
            desiredConnected = true
            if (!syncPort()) ensureReconnectLoop()
        }
    }

    /** 执行底层连接同步（参数变化时自动调用） */
    private fun syncPort(reconnecting: Boolean = false): Boolean {
        val settings = currentConnectionSettings()
        val port = settings.port
        if (port.isEmpty()) {
            connectionState = SerialConnectionState.ERROR
            connectionDetail = "serial port not selected"
            return false
        }
        connectionState = if (reconnecting) SerialConnectionState.RECONNECTING else SerialConnectionState.CONNECTING
        connectionDetail = port
        if (leasedPort != null && leasedPort != port) {
            closeCurrentPort(message = "", preserveIntent = true)
        }
        if (!SerialPortLeaseRegistry.acquire(port, leaseOwner)) {
            val owner = RuntimeMessages.otherPage(prefs.currentLanguage)
            resetRecorder()
            isPortOpen = false
            appliedSettings = null
            connectionState = SerialConnectionState.ERROR
            connectionDetail = "$port is used by $owner"
            addLog(RuntimeMessages.portOccupied(prefs.currentLanguage, port, owner), LogType.INFO)
            return false
        }
        closingIntentionally = true
        val success = try {
            serialComm.openPort(
                settings.port,
                settings.baudRate,
                settings.dataBits,
                settings.stopBitsIndex,
                settings.parityIndex,
                settings.flowControlIndex
            )
        } finally {
            closingIntentionally = false
        }
        isPortOpen = success
        if (success) {
            leasedPort = port
            appliedSettings = settings
            connectionState = SerialConnectionState.CONNECTED
            connectionDetail = connectionSettingsAppliedMessage(settings)
            reconnectJob?.cancel()
            reconnectJob = null
            resetRecorder()
            startLineSignalPolling()
            addLog(connectionSettingsAppliedMessage(settings), LogType.INFO)
        } else {
            SerialPortLeaseRegistry.release(port, leaseOwner)
            leasedPort = null
            appliedSettings = null
            resetRecorder()
            val detail = serialComm.lastErrorMessage?.let { ": $it" }.orEmpty()
            connectionState = if (reconnecting) SerialConnectionState.RECONNECTING else SerialConnectionState.ERROR
            connectionDetail = serialComm.lastErrorMessage.orEmpty()
            addLog(RuntimeMessages.connectionFailed(prefs.currentLanguage, port, detail), LogType.INFO)
        }
        return success
    }

    /** 发送字符串或 HEX 至串口 */
    fun sendData(data: String): Boolean {
        if (!isPortOpen || data.isBlank()) return false
        val bytes = encodeSendData(data) ?: return false
        val hexMode = prefs.hexSend
        viewModelScope.launch { sendBytes(data, bytes, hexMode) }
        return true
    }

    /** 手动发送当前输入；循环发送和快捷指令不会触发清空。 */
    fun sendCurrentInput(): Boolean {
        val accepted = sendData(inputText)
        if (accepted && prefs.clearAfterSend) inputText = ""
        return accepted
    }

    private fun encodeSendData(data: String): ByteArray? {
        val payload = if (prefs.hexSend) {
            val result = HexInputParser.parse(data)
            if (!result.isValid) {
                addLog(RuntimeMessages.invalidHex(prefs.currentLanguage), LogType.INFO)
                return null
            }
            result.bytes ?: byteArrayOf()
        } else {
            TextEscapeParser.decodeToByteArray(data)
        }
        return if (prefs.hexSend) payload else payload + prefs.sendLineEnding.bytes
    }

    fun canSend(data: String): Boolean {
        if (!isPortOpen || data.isBlank()) return false
        return !prefs.hexSend || HexInputParser.parse(data).isValid
    }

    fun pendingSendByteCount(data: String = inputText): Int? {
        if (data.isBlank()) return 0
        return if (prefs.hexSend) {
            HexInputParser.parse(data).bytes?.size
        } else {
            TextEscapeParser.decodeToByteArray(data).size + prefs.sendLineEnding.bytes.size
        }
    }

    private suspend fun sendBytes(source: String, bytes: ByteArray, hexMode: Boolean) {
        if (bytes.isEmpty()) return
        val sent = withContext(Dispatchers.IO) { serialComm.write(bytes) }
        if (sent < 0) {
            handleUnexpectedDisconnect(RuntimeMessages.sendFailed(prefs.currentLanguage, serialComm.lastErrorMessage.orEmpty()))
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

    fun setAutoReconnect(enabled: Boolean) {
        prefs.autoReconnect = enabled
        if (enabled && desiredConnected && !isPortOpen) {
            ensureReconnectLoop()
        } else if (!enabled) {
            reconnectJob?.cancel()
            reconnectJob = null
            if (!isPortOpen) connectionState = SerialConnectionState.DISCONNECTED
        }
    }

    fun setDtr(enabled: Boolean): Boolean {
        if (!isPortOpen || config.flowControlIndex == 2) return false
        val changed = serialComm.setDtr(enabled)
        if (changed) dtrEnabled = enabled
        return changed
    }

    fun setRts(enabled: Boolean): Boolean {
        if (!isPortOpen || config.flowControlIndex == 1) return false
        val changed = serialComm.setRts(enabled)
        if (changed) rtsEnabled = enabled
        return changed
    }

    fun setBreak(enabled: Boolean): Boolean {
        if (!isPortOpen) return false
        val changed = serialComm.setBreak(enabled)
        if (changed) breakEnabled = enabled
        return changed
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
            if (isConnectionActive) togglePort()
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
                        handleUnexpectedDisconnect(RuntimeMessages.sendFailed(prefs.currentLanguage, serialComm.lastErrorMessage.orEmpty()))
                    }
                }
            }
            val listener: (ByteArray) -> Unit = { data -> newSimulator.checkData(data) }
            serialComm.addDataListener(listener)
            simulator = newSimulator
            simulatorListener = listener
            isSimulating = true
            simulatorConfigName = file.name
            addLog(RuntimeMessages.simulatorLoaded(prefs.currentLanguage, file.name), LogType.INFO)
        } else {
            clearSimulator()
            simulatorConfigName = ""
            addLog(RuntimeMessages.simulatorFailed(prefs.currentLanguage), LogType.INFO)
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
            addLog(RuntimeMessages.invalidLoopInterval(prefs.currentLanguage), LogType.INFO)
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
                                RuntimeMessages.loopStopped(prefs.currentLanguage, RuntimeMessages.invalidHex(prefs.currentLanguage)),
                                LogType.INFO
                            )
                            break
                        }
                        val bytes = if (hexMode) parseResult?.bytes ?: byteArrayOf() else {
                            TextEscapeParser.decodeToByteArray(data) + prefs.sendLineEnding.bytes
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
    fun clearRx() {
        rxCounterBaseline = receivedByteTotal.get()
        rxCount = 0
    }

    // ---- 资源回收 ----

    public override fun onCleared() {
        desiredConnected = false
        reconnectJob?.cancel()
        lineSignalJob?.cancel()
        closeCurrentPort()
        serialComm.clearDataListeners()
        serialComm.clearTransportListeners()
        receiveQueue.close()
        clearSimulator()
        stopLoopSend()
        logs.clear()
        txCount = 0
        rxCount = 0
        super.onCleared()
    }

    private fun closeCurrentPort(message: String? = null, preserveIntent: Boolean = false) {
        val resolvedMessage = message ?: RuntimeMessages.disconnected(prefs.currentLanguage)
        val wasConnected = isPortOpen || leasedPort != null
        closingIntentionally = true
        try {
            serialComm.closePort()
        } finally {
            closingIntentionally = false
        }
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
        appliedSettings = null
        resetRecorder()
        isPortOpen = false
        lineSignalJob?.cancel()
        lineSignalJob = null
        lineSignals = SerialLineSignals()
        dtrEnabled = false
        rtsEnabled = false
        breakEnabled = false
        receiveAssembler.reset()
        while (receiveQueue.tryReceive().isSuccess) {
            // 丢弃旧连接仍在队列中的显示数据，避免重连后混入上一会话。
        }
        stopLoopSend()
        if (!preserveIntent) desiredConnected = false
        connectionState = SerialConnectionState.DISCONNECTED
        connectionDetail = resolvedMessage
        if (wasConnected && resolvedMessage.isNotBlank()) addLog(resolvedMessage, LogType.INFO)
    }

    private fun handleUnexpectedDisconnect(detail: String) {
        if (!isPortOpen && leasedPort == null && connectionState == SerialConnectionState.RECONNECTING) return
        val shouldReconnect = desiredConnected && prefs.autoReconnect
        closeCurrentPort(message = RuntimeMessages.connectionLost(prefs.currentLanguage, detail), preserveIntent = shouldReconnect)
        if (shouldReconnect) ensureReconnectLoop()
        else {
            connectionState = SerialConnectionState.ERROR
            connectionDetail = detail
        }
    }

    private fun handleTransportError(detail: String) {
        connectionDetail = detail
        addLog(RuntimeMessages.lineEvent(prefs.currentLanguage, detail), LogType.INFO)
    }

    private fun ensureReconnectLoop() {
        if (!desiredConnected || !prefs.autoReconnect || reconnectJob?.isActive == true) return
        connectionState = SerialConnectionState.RECONNECTING
        reconnectJob = viewModelScope.launch {
            while (isActive && desiredConnected && prefs.autoReconnect && !isPortOpen) {
                delay(prefs.reconnectIntervalMs)
                if (config.selectedPort in availablePorts && syncPort(reconnecting = true)) break
            }
        }
    }

    private fun startLineSignalPolling() {
        lineSignalJob?.cancel()
        lineSignalJob = viewModelScope.launch {
            while (isActive && isPortOpen) {
                lineSignals = withContext(Dispatchers.IO) { serialComm.lineSignals() }
                delay(250)
            }
        }
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
        prefs.highlight.setKeywords(keywords)
        prefs.highlight.enabled = highlightConfig.optionalBoolean("enabled") ?: keywords.isNotEmpty()
        prefs.highlight.ignoreCase = highlightConfig.optionalBoolean("ignoreCase") ?: true
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
