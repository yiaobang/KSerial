package com.yiaobang.kserial.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.i18n.RuntimeMessages
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.NumericStreamParser
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.serial.EndpointAddress
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.TransportRouter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * 串口绘图状态：把收到的文本行解析成多列数值，维护一个滑动窗口供曲线绘制。
 *
 * 采样缓冲用普通集合 + 版本号触发重绘，而不是把上千个 DoubleArray 放进 Compose 快照状态：
 * 高采样率下每帧都改状态列表会带来明显的重组开销。
 */
class PlotterViewModel(
    private val transport: SerialTransport = TransportRouter(),
    /**
     * 是否监视"端口从系统列表消失并自动断开"。
     * 单元测试没有真实端口枚举，注入 false 可避免刚打开就被判为消失。
     */
    private val monitorPortLoss: Boolean = true
) : ViewModel() {

    /** 绘图窗口长度（采样点数）。 */
    val windowOptions = listOf(200, 500, 1000, 2000)

    var language by mutableStateOf(Language.fromLocale())
    var port by mutableStateOf("")
    var baudRate by mutableIntStateOf(115200)
    var isOpen by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var windowSize by mutableIntStateOf(500)
        private set
    var channelCount by mutableIntStateOf(0)
        private set
    var droppedLines by mutableIntStateOf(0)
        private set

    /** 打开后累计收到的原始字节数（含未成行的），用于诊断"数据在到达但没有曲线"。 */
    var receivedBytes by mutableLongStateOf(0L)
        private set

    /** 还没等到换行符的悬挂字节数；大于 0 说明发送端没带行尾，采样永远不会产生。 */
    var pendingBytes by mutableIntStateOf(0)
        private set

    /** 缓冲版本号：+1 时 UI 重新读取 [samples]。 */
    var revision by mutableIntStateOf(0)
        private set

    val logs = androidx.compose.runtime.mutableStateListOf<LogEntry>()

    private val dataListener: (ByteArray) -> Unit = { chunk -> onData(chunk) }
    private val buffer = ArrayDeque<DoubleArray>()
    private var parser = NumericStreamParser(maxChannels = 8)
    private var leasedPort: String? = null
    private val leaseOwner = "串口绘图-${UUID.randomUUID()}"
    private var nextLogId = 1L

    val availablePorts = PortMonitor.availablePorts

    init {
        PortMonitor.start()
        if (monitorPortLoss) {
            viewModelScope.launch {
                PortMonitor.availablePorts.collect { ports ->
                    val current = leasedPort ?: return@collect
                    // tcp:// / udp:// 端点不会出现在系统串口枚举里，必须跳过判定。
                    if (EndpointAddress.lostFromPortList(current, ports)) {
                        close()
                        addLog(RuntimeMessages.portDisconnected(language, current), LogType.INFO)
                    }
                }
            }
        }
    }

    fun open() {
        if (isOpen) return
        if (port.isBlank()) {
            addLog(RuntimeMessages.endpointNoPort(language, "Plotter"), LogType.INFO)
            return
        }
        if (!SerialPortLeaseRegistry.acquire(port, leaseOwner)) {
            addLog(RuntimeMessages.portOccupied(language, port, RuntimeMessages.otherPage(language)), LogType.INFO)
            return
        }
        val opened = transport.openPort(
            portName = port,
            baudRate = baudRate,
            dataBits = 8,
            stopBitsIndex = 0,
            parityIndex = 0,
            flowControlIndex = 0
        )
        isOpen = opened
        if (opened) {
            leasedPort = port
            transport.addDataListener(dataListener)
            addLog(RuntimeMessages.endpointOpened(language, "Plotter", port, true), LogType.INFO)
        } else {
            SerialPortLeaseRegistry.release(port, leaseOwner)
            addLog(RuntimeMessages.endpointOpened(language, "Plotter", port, false), LogType.INFO)
        }
    }

    fun close() {
        if (!isOpen) return
        runCatching { transport.removeDataListener(dataListener) }
        transport.closePort()
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
        isOpen = false
        paused = false
        addLog(RuntimeMessages.endpointClosed(language, "Plotter"), LogType.INFO)
    }

    fun togglePause() {
        paused = !paused
    }

    fun clear() {
        buffer.clear()
        parser.reset()
        channelCount = 0
        droppedLines = 0
        receivedBytes = 0L
        pendingBytes = 0
        revision++
    }

    fun setWindow(size: Int) {
        windowSize = size.coerceIn(50, 10_000)
        trim()
        revision++
    }

    /** 当前窗口内的采样副本，UI 只在绘制时读取一次。 */
    fun samples(): List<DoubleArray> = synchronized(buffer) { buffer.toList() }

    fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
        if (logs.size >= 300) logs.removeAt(0)
        logs.add(LogEntry(time, type, content, nextLogId++))
    }

    private fun onData(chunk: ByteArray) {
        receivedBytes += chunk.size
        if (paused) return
        val parsed = parser.feed(chunk)
        pendingBytes = parser.pendingSize
        if (parsed.isEmpty()) return
        var changed = false
        synchronized(buffer) {
            parsed.forEach { sample ->
                if (sample.size > channelCount) channelCount = sample.size
                buffer.addLast(sample)
                changed = true
            }
            while (buffer.size > windowSize) buffer.removeFirst()
        }
        if (changed) revision++
    }

    private fun trim() {
        synchronized(buffer) {
            while (buffer.size > windowSize) buffer.removeFirst()
        }
    }

    public override fun onCleared() {
        runCatching { transport.removeDataListener(dataListener) }
        transport.closePort()
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
        viewModelScope.cancel()
        super.onCleared()
    }

    /** 选端口时同时给一个合理默认值，避免新页面必须手填。 */
    fun selectPort(name: String) {
        if (!isOpen) port = name
    }

    fun setBaud(rate: Int) {
        if (!isOpen) baudRate = rate
    }

    companion object {
        val BAUDS = SerialPortConfig.BAUD_RATES
    }
}
