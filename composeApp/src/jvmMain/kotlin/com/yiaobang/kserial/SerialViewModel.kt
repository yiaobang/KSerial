package com.yiaobang.kserial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yiaobang.kserial.i18n.AppStrings
import com.yiaobang.kserial.model.AppPreferences
import com.yiaobang.kserial.model.DataWriteFile
import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.utils.hexToByteArray
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

/**
 * 串口视图模型类
 * 职责：业务逻辑协调层，连接 UI 层与底层通信层
 * 串口参数由 [config] 管理，UI 偏好由 [prefs] 管理
 */
class SerialViewModel : ViewModel() {

    // ---- 底层通信实例（每个 VM 独立，实现窗口级隔离）----
    val serialComm = SerialComm()

    // ---- 状态持有者（职责分离）----
    /** 串口连接参数 */
    val config = SerialPortConfig()

    /** UI 显示偏好 */
    val prefs = AppPreferences()

    // ---- 当前语言字典（由 prefs.currentLanguage 驱动）----
    val strings get() = AppStrings.of(prefs.currentLanguage)

    // ---- 串口列表 ----
    var availablePorts by mutableStateOf(emptyList<String>())

    // ---- 连接状态 ----
    var isPortOpen by mutableStateOf(false)

    // ---- 模拟状态 ----
    var isSimulating by mutableStateOf(false)

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

    init {
        // 启动全局串口监视器
        PortMonitor.start()

        // 观察串口列表并同步 UI；首次检测到串口时自动连接
        viewModelScope.launch {
            PortMonitor.availablePorts.collect { ports ->
                availablePorts = ports
                if (config.selectedPort.isEmpty() && ports.isNotEmpty()) {
                    config.selectedPort = ports[0]
                    syncPort()
                }
            }
        }

        // 注册串口数据回调（解耦：SerialComm 不再持有任何外部依赖）
        serialComm.addDataListener { data ->
            dataWriteFile?.serialCommReceive(data)
        }

        // 观察串口原始数据流并推送到 UI 列表
        observeDataFlow()

        // 监听串口参数变化并自动重连
        observeConfigChanges()
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
            serialComm.closePort()
            dataWriteFile = null
            isPortOpen = false
            stopLoopSend()
            addLog("连接已断开", LogType.INFO)
        } else {
            syncPort()
        }
    }

    /** 执行底层连接同步（参数变化时自动调用） */
    private fun syncPort() {
        val port = config.selectedPort
        if (port.isEmpty()) return
        val success = serialComm.openPort(
            port, config.baudRate, config.dataBits,
            config.stopBitsIndex, config.parityIndex, config.flowControlIndex
        )
        isPortOpen = success
        if (success) {
            // 连接成功后初始化持久化记录器
            dataWriteFile = DataWriteFile(port)
            addLog("串口 $port 已就绪", LogType.INFO)
        } else {
            dataWriteFile = null
            addLog("同步失败: $port", LogType.INFO)
        }
    }

    /** 发送字符串或 HEX 至串口 */
    fun sendData(data: String) {
        if (!isPortOpen || data.isBlank()) return
        val bytes = if (prefs.hexSend) data.hexToByteArray() ?: byteArrayOf() else data.toByteArray()
        if (bytes.isNotEmpty()) {
            val sent = serialComm.write(bytes)
            if (sent >= 0) {
                // 发送成功后记录到持久化文件
                dataWriteFile?.serialCommSend(bytes)
                txCount += sent
                addLog(if (prefs.hexSend) bytes.toHexString() else data, LogType.TX)
            }
        }
    }

    /** 加载 JSON 设备模拟器配置文件 */
    fun loadSimulator(file: File) {
        val simulator = DeviceSimulator.createDeviceSimulator(file)
        if (simulator != null) {
            // 通过 SerialComm 的监听器机制挂载模拟器，无需 SerialComm 持有其引用
            simulator.serialComm = serialComm
            serialComm.addDataListener { data -> simulator.checkData(data) }
            isSimulating = true
            addLog("模拟器配置加载成功: ${file.name}", LogType.INFO)
        } else {
            addLog("模拟器配置文件解析失败", LogType.INFO)
            isSimulating = false
        }
    }

    /** 追加日志条目，最多保留 1000 条 */
    fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss").format(Date())
        if (logs.size > 1000) logs.removeAt(0)
        logs.add(LogEntry(time, type, content))
    }

    // ---- 循环发送 ----

    fun startLoopSend() {
        stopLoopSend()
        if (prefs.loopSendEnabled && isPortOpen) {
            loopJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    if (inputText.isNotBlank()) sendData(inputText)
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
        serialComm.closePort()
        serialComm.clearDataListeners()
        stopLoopSend()
        logs.clear()
        txCount = 0
        rxCount = 0
        super.onCleared()
    }
}


