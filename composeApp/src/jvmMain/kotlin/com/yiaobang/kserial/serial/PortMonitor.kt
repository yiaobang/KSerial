package com.yiaobang.kserial.serial

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * 全局串口监视器 (1:1 复刻 Java PortMonitor)
 * 职责：作为单例存在，定时扫描系统串口，避免多窗口并发扫描冲突。
 */
object PortMonitor {
    // 内部持有的可观察状态流，存储当前所有串口名称
    private val _availablePorts = MutableStateFlow<List<String>>(emptyList())
    val availablePorts = _availablePorts.asStateFlow()
    private val _portDetails = MutableStateFlow<List<SerialPortInfo>>(emptyList())
    val portDetails = _portDetails.asStateFlow()

    // 协程作用域，专门用于串口扫描任务
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    /**
     * 开启扫描任务 (如果已在运行则略过)
     * 模拟 Java 版的 PortMonitor.init()
     */
    @Synchronized
    fun start() {
        if (scanJob != null) return
        val job = scope.launch {
            while (isActive) {
                refreshNow()
                delay(2000.milliseconds)
            }
        }
        scanJob = job
        job.invokeOnCompletion {
            synchronized(this@PortMonitor) {
                if (scanJob === job) scanJob = null
            }
        }
    }

    /**
     * 停止扫描任务 (通常在应用退出时调用)
     */
    @Synchronized
    fun stop() {
        scanJob?.cancel()
        scanJob = null
    }

    /** UI 可主动刷新；周期扫描仍作为热插拔兜底。 */
    fun refreshNow() {
        runCatching {
            SerialPort.getCommPorts().map { port ->
                SerialPortInfo(
                    systemName = port.systemPortName,
                    descriptiveName = port.descriptivePortName.orEmpty(),
                    description = port.portDescription.orEmpty(),
                    vendorId = port.vendorID.takeIf { it >= 0 },
                    productId = port.productID.takeIf { it >= 0 }
                )
            }
        }.onSuccess { details ->
            _portDetails.value = details
            val names = details.map(SerialPortInfo::systemName)
            if (names != _availablePorts.value) _availablePorts.value = names
        }
    }
}
