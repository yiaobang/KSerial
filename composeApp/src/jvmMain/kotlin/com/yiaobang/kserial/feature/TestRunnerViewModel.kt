package com.yiaobang.kserial.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.i18n.RuntimeMessages
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.TestSequence
import com.yiaobang.kserial.model.TestSequenceRunner
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.TransportRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * 测试序列工作区状态：加载 .conf 序列、打开端口、逐步执行并维护结果表。
 *
 * 执行逻辑全部委托给 [TestSequenceRunner]，这里只负责端口生命周期和界面状态，
 * 因此同一套断言在 CLI 里也能量复用（见 cli 包）。
 */
class TestRunnerViewModel(
    private val transport: SerialTransport = TransportRouter()
) : ViewModel() {

    val results = mutableStateListOf<TestSequenceRunner.StepResult>()
    val logs = mutableStateListOf<LogEntry>()

    var language by mutableStateOf(Language.fromLocale())
    var loadedName by mutableStateOf("")
        private set
    var sequence by mutableStateOf<TestSequence?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var summary by mutableStateOf("")
        private set
    var lastReport by mutableStateOf<TestSequenceRunner.Report?>(null)
        private set

    private var runJob: Job? = null
    private var leasedPort: String? = null
    private val leaseOwner = "测试序列-${UUID.randomUUID()}"
    private var nextLogId = 1L

    val canRun: Boolean
        get() = !running && sequence != null

    fun loadConfig(file: File) {
        if (running) return
        runCatching { TestSequence.load(file) }
            .onSuccess { loaded ->
                sequence = loaded
                loadedName = file.name
                results.clear()
                summary = ""
                lastReport = null
                addLog(RuntimeMessages.testLoaded(language, file.name, loaded.steps.size), LogType.INFO)
            }
            .onFailure { error ->
                sequence = null
                loadedName = file.name
                addLog(RuntimeMessages.testLoadFailed(language, error.message ?: "unknown"), LogType.INFO)
            }
    }

    fun runSequence() {
        val loaded = sequence ?: return
        if (running) return
        val port = loaded.connection.port
        if (port.isBlank()) {
            addLog(RuntimeMessages.testOpenFailed(language, "connection.port is empty"), LogType.INFO)
            return
        }
        if (!SerialPortLeaseRegistry.acquire(port, leaseOwner)) {
            addLog(RuntimeMessages.portOccupied(language, port, RuntimeMessages.otherPage(language)), LogType.INFO)
            return
        }
        val opened = transport.openPort(
            portName = port,
            baudRate = loaded.connection.baudRate,
            dataBits = loaded.connection.dataBits,
            stopBitsIndex = loaded.connection.stopBitsIndex,
            parityIndex = loaded.connection.parityIndex,
            flowControlIndex = loaded.connection.flowControlIndex
        )
        if (!opened) {
            SerialPortLeaseRegistry.release(port, leaseOwner)
            addLog(RuntimeMessages.testOpenFailed(language, port), LogType.INFO)
            return
        }
        leasedPort = port
        results.clear()
        summary = ""
        running = true
        addLog(RuntimeMessages.testStarted(language, loaded.steps.size), LogType.INFO)

        runJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = TestSequenceRunner(transport).run(loaded) { result ->
                    viewModelScope.launch { results += result }
                    addLog(
                        "${result.name}: ${if (result.passed) "PASS" else "FAIL"}",
                        if (result.passed) LogType.RX else LogType.TX
                    )
                }
                lastReport = report
                summary = RuntimeMessages.testFinished(language, report.passedCount, report.results.size)
                addLog(summary, if (report.allPassed) LogType.RX else LogType.TX)
            } catch (error: Exception) {
                if (isActive) addLog(RuntimeMessages.testLoadFailed(language, error.message ?: "unknown"), LogType.INFO)
            } finally {
                closePort()
                running = false
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        closePort()
        running = false
    }

    fun clearResults() {
        results.clear()
        summary = ""
        lastReport = null
    }

    /** 把结果导出成纯文本报告（CLI 直接打印同一份格式）。 */
    fun reportText(): String {
        val report = lastReport ?: return summary
        return buildString {
            appendLine("KSerial test report - $loadedName")
            appendLine("passed ${report.passedCount} / ${report.results.size} (${report.totalMs} ms)")
            report.results.forEach { result ->
                val status = if (result.passed) "PASS" else "FAIL"
                appendLine("[${result.index + 1}] $status ${result.name}  expected=\"${result.expected}\" actual=\"${result.actual}\"")
            }
        }
    }

    fun addLog(content: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
        if (logs.size >= 500) logs.removeAt(0)
        logs.add(LogEntry(time, type, content, nextLogId++))
    }

    private fun closePort() {
        transport.closePort()
        leasedPort?.let { SerialPortLeaseRegistry.release(it, leaseOwner) }
        leasedPort = null
    }

    public override fun onCleared() {
        runJob?.cancel()
        closePort()
        viewModelScope.cancel()
        super.onCleared()
    }
}
