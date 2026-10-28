package com.yiaobang.kserial.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.i18n.RuntimeMessages
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.model.TrafficRecorder
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransportEvent
import com.yiaobang.kserial.utils.DesktopActions
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.SimulatorConfigGenerator
import com.yiaobang.kserial.utils.toHexDisplayString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * 串口录制工作区：把两个端点用软件"接成一根线"，并录制经过这根线的问答。
 *
 * 典型用法：商业上位机软件接「软件侧」，真实设备接「设备侧」。软件照常操作设备，
 * KSerial 在旁边透明转发并记录「软件指令 → 设备应答」，停止后导出成设备模拟器配置，
 * 以后没有真机也能复现同样的对话。
 *
 * 手动模式面向另一个方向：没有上位机软件、设备直连电脑只有一个口时，
 * 终端本身就是上位机。此时只打开「设备侧」，在日志下方手动发指令，
 * 录到的是「设备怎么回应协议」，与双口模式互补而非重复。
 *
 * 与串口桥接的区别是刻意的：桥接面向多端点路由，录制需要的是稳定的一对一对应关系，
 * 否则无法判断某条应答到底属于哪个请求。
 */
class CaptureViewModel(
    /** 端点 0 = 软件侧，端点 1 = 设备侧；测试可注入内存传输。 */
    val endpoints: List<GatewayEndpoint> = listOf(
        GatewayEndpoint(index = 0, id = SOFTWARE_ID, name = SOFTWARE_ID),
        GatewayEndpoint(index = 1, id = DEVICE_ID, name = DEVICE_ID)
    )
) : ViewModel() {

    val softwareSide: GatewayEndpoint get() = endpoints[0]
    val deviceSide: GatewayEndpoint get() = endpoints[1]

    val logs = mutableStateListOf<LogEntry>()
    var language by mutableStateOf(Language.fromLocale())

    var forwarding by mutableStateOf(false)
        private set

    var recording by mutableStateOf(false)
        private set

    var recordedPairCount by mutableIntStateOf(0)
        private set

    var bytesToDevice by mutableLongStateOf(0L)
        private set

    var bytesToSoftware by mutableLongStateOf(0L)
        private set

    /** 手动模式：不占用软件侧端点，由用户在页面输入区直接操作设备侧。 */
    var manualMode by mutableStateOf(false)
        private set

    var manualInput by mutableStateOf("")

    var manualHexMode by mutableStateOf(false)

    var manualLineEnding by mutableStateOf(SendLineEnding.CRLF)

    /** 最近一次导出的配置文件，供界面提示路径。 */
    var lastExportPath by mutableStateOf("")
        private set

    private val recorder = TrafficRecorder()
    private var nextLogId = 1L

    /**
     * 问答计数只在"指令发出"和"该指令的首个应答到达"时重算：
     * 之后的续帧只是在延长同一组应答，不会改变组数，没必要对全量事件做一次配对扫描。
     */
    private var pairCountDirty = false

    val availablePorts = PortMonitor.availablePorts

    val canStartForwarding: Boolean
        get() = !forwarding && deviceSide.selectedPort.isNotBlank() &&
            (manualMode || softwareSide.selectedPort.isNotBlank())

    init {
        PortMonitor.start()
        // 监听器只在初始化时注册一次：TransportRouter 会在切换底层实现时保留它们。
        endpoints.forEach { endpoint ->
            // 串口原始数据进队列：双口模式由消费协程转发到对端，手动模式录制并显示。
            endpoint.serialComm.addDataListener { data ->
                endpoint.receivedTotal.addAndGet(data.size.toLong())
                if (!endpoint.receiveQueue.trySend(data).isSuccess) {
                    endpoint.droppedBytes.addAndGet(data.size.toLong())
                }
            }
            endpoint.serialComm.addTransportListener { event ->
                viewModelScope.launch {
                    if (forwarding) {
                        when (event) {
                            SerialTransportEvent.Disconnected ->
                                closeEndpoint(endpoint, RuntimeMessages.portDisconnected(language, endpoint.selectedPort))
                            is SerialTransportEvent.Error -> addLog(RuntimeMessages.lineEvent(language, event.detail), LogType.INFO)
                        }
                    }
                }
            }
        }
        // 端口消失时立即断开，避免界面显示"仍在转发"但实际链路已断。
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                endpoints
                    .filter { endpoint ->
                        val port = endpoint.leasedPort
                        endpoint.isOpen && port != null &&
                            com.yiaobang.kserial.serial.EndpointAddress.lostFromPortList(port, ports)
                    }
                    .forEach { closeEndpoint(it, RuntimeMessages.portDisconnected(language, it.selectedPort)) }
            }
        }
    }

    fun displayName(endpoint: GatewayEndpoint): String = when (endpoint.id) {
        SOFTWARE_ID -> RuntimeMessages.softwareSide(language)
        else -> RuntimeMessages.deviceSide(language)
    }

    /** 模式切换只允许在空闲时进行，避免转发中换拓扑。 */
    fun setManualCapture(enabled: Boolean) {
        if (forwarding) return
        manualMode = enabled
    }

    fun startForwarding() {
        if (forwarding) return
        if (deviceSide.selectedPort.isBlank()) {
            addLog(RuntimeMessages.captureNeedPorts(language), LogType.INFO)
            return
        }
        if (!manualMode) {
            if (softwareSide.selectedPort.isBlank()) {
                addLog(RuntimeMessages.captureNeedPorts(language), LogType.INFO)
                return
            }
            if (softwareSide.selectedPort == deviceSide.selectedPort) {
                addLog(RuntimeMessages.captureSamePort(language, softwareSide.selectedPort), LogType.INFO)
                return
            }
        }
        if (manualMode) {
            if (!openEndpoint(deviceSide)) return
        } else {
            val openedA = openEndpoint(softwareSide)
            val openedB = if (openedA) openEndpoint(deviceSide) else false
            if (!openedA || !openedB) {
                closeEndpoint(softwareSide)
                closeEndpoint(deviceSide)
                return
            }
        }
        forwarding = true
        bytesToDevice = 0
        bytesToSoftware = 0
        // 丢弃上一次转发残留的帧，避免重启后先"补发"旧数据。
        endpoints.forEach { endpoint -> while (endpoint.receiveQueue.tryReceive().isSuccess) Unit }
        if (manualMode) {
            // 手动模式没有"对端"可转发：设备侧回来的数据按应答录制并显示。
            deviceSide.receiveJob?.cancel()
            deviceSide.receiveJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    val data = deviceSide.receiveQueue.receiveCatching().getOrNull() ?: break
                    receiveFromDevice(data)
                }
            }
        } else {
            startPump(softwareSide, deviceSide)
            startPump(deviceSide, softwareSide)
        }
        addLog(
            if (manualMode) RuntimeMessages.captureManualStarted(language) else RuntimeMessages.captureStarted(language),
            LogType.INFO
        )
    }

    fun stopForwarding() {
        if (!forwarding) return
        forwarding = false
        endpoints.forEach { it.receiveJob?.cancel() }
        closeEndpoint(softwareSide)
        closeEndpoint(deviceSide)
        addLog(RuntimeMessages.captureStopped(language), LogType.INFO)
    }

    fun startRecording() {
        if (recording) return
        recorder.clear()
        recordedPairCount = 0
        recording = true
        pairCountDirty = false
        addLog(RuntimeMessages.captureRecordingStarted(language), LogType.INFO)
    }

    /** 仅停止录制、不弹导出对话框；供测试与后续"另存"流程复用。 */
    internal fun stopRecordingOnly() {
        recording = false
    }

    /** 当前录制到的问答对快照；配合 [stopRecordingOnly] 供无头环境导出。 */
    internal fun recordedPairs(): List<TrafficRecorder.TrafficPair> = recorder.pairs()

    /** 停止录制并导出模拟器配置；成功返回写入的文件。 */
    fun stopRecordingAndExport(): File? {
        if (!recording) return null
        recording = false
        val pairs = recorder.pairs()
        if (pairs.isEmpty()) {
            addLog(RuntimeMessages.captureRecordingEmpty(language), LogType.INFO)
            return null
        }
        val device = deviceSide
        val endpoint = device.selectedPort.takeIf { it.isNotBlank() }?.let {
            SimulatorConfigGenerator.EndpointInfo(
                port = it,
                baudRate = device.baudRate,
                dataBits = device.dataBits,
                stopBitsIndex = device.stopBitsIndex,
                parityIndex = device.parityIndex,
                flowControlIndex = device.flowControlIndex
            )
        }
        return try {
            val content = SimulatorConfigGenerator.generate(pairs, endpoint)
            val target = DesktopActions.saveConfigFile(
                RuntimeMessages.simulatorExportDialogTitle(language),
                "KSerial-capture-${SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())}.conf"
            ) ?: run {
                addLog(RuntimeMessages.captureRecordingHeld(language, pairs.size), LogType.INFO)
                return null
            }
            target.writeText(content)
            lastExportPath = target.absolutePath
            addLog(RuntimeMessages.simulatorConfigExported(language, target.name, pairs.size), LogType.INFO)
            target
        } catch (error: Exception) {
            addLog(RuntimeMessages.simulatorConfigExportFailed(language, error.message ?: "unknown"), LogType.INFO)
            null
        }
    }

    fun clearLogs() {
        logs.clear()
    }

    fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
        if (logs.size >= 1000) logs.removeAt(0)
        logs.add(LogEntry(time, type, content, nextLogId++))
    }

    private fun startPump(source: GatewayEndpoint, target: GatewayEndpoint) {
        source.receiveJob?.cancel()
        source.receiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val data = source.receiveQueue.receiveCatching().getOrNull() ?: break
                forward(source, target, data)
            }
        }
    }

    /** 单帧转发：写目标、计数、按方向录制。消费循环和单元测试共用这一条路径。 */
    internal suspend fun forward(source: GatewayEndpoint, target: GatewayEndpoint, data: ByteArray) {
        val written = withContext(Dispatchers.IO) { target.serialComm.write(data) }
        if (written <= 0) {
            stopForwarding()
            addLog(RuntimeMessages.forwardingFailed(language, displayName(source), displayName(target)), LogType.INFO)
            return
        }
        target.txCount += written
        val payload = data.copyOf(written.coerceAtMost(data.size))
        val fromSoftware = source === softwareSide
        if (fromSoftware) bytesToDevice += written else bytesToSoftware += written

        if (recording) {
            // 软件侧发出的是请求，设备侧回来的是应答；这与 TrafficRecorder 的 TX/RX 语义一致。
            if (fromSoftware) {
                recorder.onTx(payload)
                recordedPairCount = recorder.pairCount()
                pairCountDirty = true
            } else {
                recorder.onRx(payload)
                if (pairCountDirty) {
                    recordedPairCount = recorder.pairCount()
                    pairCountDirty = false
                }
            }
        }

        addLog(
            RuntimeMessages.forwarded(language, displayName(source), displayName(target), formatData(payload)),
            if (fromSoftware) LogType.TX else LogType.RX
        )
    }

    /** 手动模式：把输入区内容按当前编码与行尾设置打包。返回 null 表示无需发送或输入非法。 */
    internal fun buildManualPayload(): ByteArray? {
        val text = manualInput
        if (manualHexMode) {
            if (text.isBlank()) return null
            val parsed = HexInputParser.parse(text)
            if (!parsed.isValid || parsed.bytes == null) {
                addLog(RuntimeMessages.manualInvalidHex(language), LogType.INFO)
                return null
            }
            return parsed.bytes
        }
        if (text.isEmpty()) return null
        val ending = when (manualLineEnding) {
            SendLineEnding.NONE -> ""
            SendLineEnding.LF -> "\n"
            SendLineEnding.CR -> "\r"
            SendLineEnding.CRLF -> "\r\n"
        }
        return (text + ending).encodeToByteArray()
    }

    /** 发送输入区内容：本机在手动模式里扮演"软件侧"。 */
    fun sendManualInput() {
        if (!forwarding || !manualMode) return
        val payload = buildManualPayload() ?: return
        manualInput = ""
        viewModelScope.launch { manualSend(payload) }
    }

    /** 手动发送：写设备侧、计数并作为请求录制。消费路径与单元测试共用。 */
    internal suspend fun manualSend(data: ByteArray) {
        val written = withContext(Dispatchers.IO) { deviceSide.serialComm.write(data) }
        if (written <= 0) {
            stopForwarding()
            addLog(RuntimeMessages.forwardingFailed(language, displayName(softwareSide), displayName(deviceSide)), LogType.INFO)
            return
        }
        deviceSide.txCount += written
        bytesToDevice += written
        val payload = data.copyOf(written.coerceAtMost(data.size))
        if (recording) {
            recorder.onTx(payload)
            recordedPairCount = recorder.pairCount()
            pairCountDirty = true
        }
        addLog(
            RuntimeMessages.forwarded(language, displayName(softwareSide), displayName(deviceSide), formatData(payload)),
            LogType.TX
        )
    }

    /** 手动模式的接收路径：设备侧回来的数据按"应答"录制并显示。 */
    internal suspend fun receiveFromDevice(data: ByteArray) {
        deviceSide.rxCount += data.size
        bytesToSoftware += data.size
        if (recording) {
            recorder.onRx(data)
            if (pairCountDirty) {
                recordedPairCount = recorder.pairCount()
                pairCountDirty = false
            }
        }
        addLog(
            RuntimeMessages.forwarded(language, displayName(deviceSide), displayName(softwareSide), formatData(data)),
            LogType.RX
        )
    }

    private fun openEndpoint(endpoint: GatewayEndpoint): Boolean {
        val port = endpoint.selectedPort
        if (!SerialPortLeaseRegistry.acquire(port, endpoint.leaseOwner)) {
            addLog(RuntimeMessages.portOccupied(language, port, RuntimeMessages.otherPage(language)), LogType.INFO)
            return false
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
        addLog(RuntimeMessages.endpointOpened(language, displayName(endpoint), port, success), LogType.INFO)
        return success
    }

    private fun closeEndpoint(endpoint: GatewayEndpoint, message: String? = null) {
        endpoint.closingIntentionally = true
        try {
            endpoint.serialComm.closePort()
        } finally {
            endpoint.closingIntentionally = false
        }
        endpoint.leasedPort?.let { SerialPortLeaseRegistry.release(it, endpoint.leaseOwner) }
        endpoint.leasedPort = null
        endpoint.isOpen = false
        if (message != null) addLog(message, LogType.INFO)
    }

    private fun formatData(data: ByteArray): String =
        if (data.isMostlyPrintable()) String(data).trimEnd('\r', '\n') else data.toHexDisplayString()

    private fun ByteArray.isMostlyPrintable(): Boolean {
        if (isEmpty()) return true
        val printable = count { byte ->
            val value = byte.toInt() and 0xFF
            value in 0x20..0x7E || value == '\n'.code || value == '\r'.code || value == '\t'.code
        }
        return printable.toDouble() / size >= 0.9
    }

    public override fun onCleared() {
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
        viewModelScope.cancel()
        super.onCleared()
    }

    private companion object {
        const val SOFTWARE_ID = "software"
        const val DEVICE_ID = "device"
    }
}
