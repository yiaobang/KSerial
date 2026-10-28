package com.yiaobang.kserial.cli

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.model.ConfigIssueSeverity
import com.yiaobang.kserial.model.ConfigValidator
import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.TestSequence
import com.yiaobang.kserial.model.TestSequenceRunner
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.serial.TransportRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/**
 * 无头 CLI：不经 GUI 直接跑桥接、设备模拟和测试序列，便于放进脚本或 CI。
 *
 * 刻意不依赖任何 Compose/ViewModel：这些页面状态依赖主线程调度器，而 CLI 需要能在
 * 没有显示环境的服务器上运行。复用的是配置解析、传输层和测试执行器这三块纯逻辑。
 *
 * 用法：
 * ```
 * kserial gateway 07-bridge.conf [--duration 60]
 * kserial simulate 08-simulator.conf [--duration 60]
 * kserial test 09-test.conf [--report result.txt]
 * ```
 */
fun runCli(args: Array<String>): Int {
    if (args.isEmpty()) {
        printHelp()
        return 2
    }
    val command = args.first().lowercase()
    val options = CliOptions.parse(args.drop(1))
    return when (command) {
        "gateway", "bridge" -> runGateway(options)
        "simulate", "simulator" -> runSimulator(options)
        "test", "run-test" -> runTests(options)
        "help", "--help", "-h" -> {
            printHelp()
            0
        }
        else -> {
            System.err.println("Unknown command: $command")
            printHelp()
            2
        }
    }
}

private fun runGateway(options: CliOptions): Int {
    val file = options.file ?: return fail("gateway requires a .conf file")
    val validation = ConfigValidator.validateGateway(file, options.language)
    validation.issues.forEach { issue ->
        val prefix = if (issue.severity == ConfigIssueSeverity.ERROR) "ERROR" else "WARN"
        println("[$prefix] ${issue.message}")
    }
    val config = validation.config
    if (!validation.isValid || config == null) {
        return fail("configuration is not runnable")
    }
    if (!config.hasPath("endpoints")) return fail("gateway.endpoints is required")

    val endpoints = config.getConfigList("endpoints").mapIndexed { index, endpointConfig ->
        HeadlessEndpoint(
            id = endpointConfig.stringOr("id", "endpoint$index"),
            name = endpointConfig.stringOr("name", endpointConfig.stringOr("id", "endpoint$index")),
            port = endpointConfig.stringOr("port", ""),
            baudRate = endpointConfig.intOr("baudRate", 9600),
            dataBits = endpointConfig.intOr("dataBits", 8),
            stopBitsIndex = stopBitsIndex(endpointConfig.stringOr("stopBits", "1")),
            parityIndex = parityIndex(endpointConfig.stringOr("parity", "none")),
            flowControlIndex = flowControlIndex(endpointConfig.stringOr("flowControl", "none"))
        )
    }
    val routes = if (config.hasPath("routes")) {
        config.getConfigList("routes").map { route ->
            route.stringListOr("sources").toSet() to route.stringListOr("targets").toSet()
        }
    } else {
        emptyList()
    }

    val transports = mutableMapOf<String, SerialTransport>()
    val forwardTotal = AtomicLong()
    try {
        endpoints.forEach { endpoint ->
            if (endpoint.port.isBlank()) {
                log("${endpoint.name}: no port configured, skipped")
                return@forEach
            }
            val transport = TransportRouter()
            if (!transport.openPort(
                    portName = endpoint.port,
                    baudRate = endpoint.baudRate,
                    dataBits = endpoint.dataBits,
                    stopBitsIndex = endpoint.stopBitsIndex,
                    parityIndex = endpoint.parityIndex,
                    flowControlIndex = endpoint.flowControlIndex
                )
            ) {
                log("${endpoint.name}: failed to open ${endpoint.port}")
                return@forEach
            }
            transports[endpoint.id] = transport
            log("${endpoint.name}: ${endpoint.port} opened")
        }
        if (transports.isEmpty()) return fail("no endpoint could be opened")

        // 有 routes 走配置路由；没有则退化成"收到的数据转发给其余所有端点"。
        endpoints.filter { it.id in transports.keys }.forEach { source ->
            val sourceTransport = transports[source.id] ?: return@forEach
            sourceTransport.addDataListener { data ->
                val targets = if (routes.isNotEmpty()) {
                    routes.filter { source.id in it.first }.flatMap { it.second }.toSet()
                } else {
                    transports.keys - source.id
                }
                targets.forEach { targetId ->
                    val target = transports[targetId] ?: return@forEach
                    target.write(data)
                    forwardTotal.addAndGet(data.size.toLong())
                }
                log("${source.name} → ${targets.joinToString(",")}: ${data.size} B")
            }
        }

        log("gateway running, ${transports.size} endpoint(s). Press Ctrl+C to stop.")
        waitForShutdown(options.durationSeconds)
        log("stopped, forwarded ${forwardTotal.get()} bytes")
        return 0
    } finally {
        transports.forEach { (_, transport) -> runCatching { transport.closePort() } }
    }
}

private fun runSimulator(options: CliOptions): Int {
    val file = options.file ?: return fail("simulate requires a .conf file")
    val simulator = DeviceSimulator.createDeviceSimulator(file)
        ?: return fail("simulator configuration is not usable: ${file.name}")

    val root = ConfigFactory.parseFile(file).resolve()
    val endpointConfig = when {
        root.hasPath("simulator.endpoint") -> root.getConfig("simulator.endpoint")
        root.hasPath("endpoint") -> root.getConfig("endpoint")
        else -> null
    } ?: return fail("simulator.endpoint.port is required in headless mode")

    val port = endpointConfig.stringOr("port", "")
    if (port.isBlank()) return fail("simulator.endpoint.port is required in headless mode")

    val transport = TransportRouter()
    if (!transport.openPort(
            portName = port,
            baudRate = endpointConfig.intOr("baudRate", 115200),
            dataBits = endpointConfig.intOr("dataBits", 8),
            stopBitsIndex = stopBitsIndex(endpointConfig.stringOr("stopBits", "1")),
            parityIndex = parityIndex(endpointConfig.stringOr("parity", "none")),
            flowControlIndex = flowControlIndex(endpointConfig.stringOr("flowControl", "none"))
        )
    ) {
        return fail("failed to open $port")
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        simulator.replyScope = scope
        simulator.onReply = { data, _ -> transport.write(data) }
        transport.addDataListener { chunk -> simulator.checkData(chunk) }
        log("simulator running on $port. Press Ctrl+C to stop.")
        waitForShutdown(options.durationSeconds)
        return 0
    } finally {
        runCatching { simulator.close() }
        scope.cancel()
        transport.closePort()
    }
}

private fun runTests(options: CliOptions): Int {
    val file = options.file ?: return fail("test requires a .conf file")
    val sequence = runCatching { TestSequence.load(file) }.getOrElse { error ->
        return fail("cannot load test sequence: ${error.message}")
    }
    val port = sequence.connection.port
    if (port.isBlank()) return fail("connection.port is required")

    val transport = TransportRouter()
    if (!transport.openPort(
            portName = port,
            baudRate = sequence.connection.baudRate,
            dataBits = sequence.connection.dataBits,
            stopBitsIndex = sequence.connection.stopBitsIndex,
            parityIndex = sequence.connection.parityIndex,
            flowControlIndex = sequence.connection.flowControlIndex
        )
    ) {
        return fail("failed to open $port")
    }

    return try {
        val report = runBlocking {
            TestSequenceRunner(transport).run(sequence) { result ->
                val status = if (result.passed) "PASS" else "FAIL"
                log("$status ${result.name}  expected=\"${result.expected}\" actual=\"${result.actual}\"")
            }
        }
        val summary = "passed ${report.passedCount} / ${report.results.size} in ${report.totalMs} ms"
        println(summary)
        options.reportPath?.let { path ->
            runCatching {
                File(path).writeText(
                    buildString {
                        appendLine("KSerial test report - ${file.name}")
                        appendLine(summary)
                        report.results.forEach { result ->
                            val status = if (result.passed) "PASS" else "FAIL"
                            appendLine("[${result.index + 1}] $status ${result.name} expected=\"${result.expected}\" actual=\"${result.actual}\" ${result.elapsedMs}ms")
                        }
                    }
                )
                log("report written to $path")
            }.onFailure { log("cannot write report: ${it.message}") }
        }
        if (report.allPassed) 0 else 1
    } finally {
        transport.closePort()
    }
}

private class HeadlessEndpoint(
    val id: String,
    val name: String,
    val port: String,
    val baudRate: Int,
    val dataBits: Int,
    val stopBitsIndex: Int,
    val parityIndex: Int,
    val flowControlIndex: Int
)

private class CliOptions(
    val file: File?,
    val durationSeconds: Long,
    val language: Language,
    val reportPath: String?
) {
    companion object {
        fun parse(args: List<String>): CliOptions {
            var file: File? = null
            var duration = 0L
            var language = Language.fromLocale()
            var report: String? = null
            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--duration", "-d" -> {
                        duration = args.getOrNull(index + 1)?.toLongOrNull() ?: 0L
                        index++
                    }

                    "--lang" -> {
                        language = when (args.getOrNull(index + 1)?.lowercase()) {
                            "zh" -> Language.ZH
                            "ja" -> Language.JA
                            else -> Language.EN
                        }
                        index++
                    }

                    "--report" -> {
                        report = args.getOrNull(index + 1)
                        index++
                    }

                    else -> if (!arg.startsWith("-") && file == null) file = File(arg)
                }
                index++
            }
            return CliOptions(file, duration, language, report)
        }
    }
}

/**
 * 等待 Ctrl+C 或指定时长；duration <= 0 表示一直运行。
 *
 * 钩子只负责放开等待并短暂留出时间，让主线程的 finally 有机会关闭端口；
 * 否则 JVM 直接退出，串口句柄要等操作系统回收。
 */
private fun waitForShutdown(durationSeconds: Long) {
    val deadline = if (durationSeconds > 0) System.currentTimeMillis() + durationSeconds * 1000 else Long.MAX_VALUE
    val stop = java.util.concurrent.CountDownLatch(1)
    val hook = Thread {
        stop.countDown()
        runCatching { Thread.sleep(1500) }
    }
    Runtime.getRuntime().addShutdownHook(hook)
    try {
        while (System.currentTimeMillis() < deadline && stop.count > 0L) {
            Thread.sleep(200)
        }
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
    } finally {
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }
}

private fun log(message: String) {
    val time = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
    println("[$time] $message")
}

private fun fail(message: String): Int {
    System.err.println(message)
    return 1
}

private fun printHelp() {
    println(
        """
        KSerial headless mode

        Usage:
          kserial gateway  <config.conf> [--duration seconds] [--lang zh|en|ja]
          kserial simulate <config.conf> [--duration seconds]
          kserial test     <config.conf> [--report result.txt]

        Notes:
          - Endpoint addresses accept COM3, /dev/ttyUSB0, tcp://host:port,
            tcp-listen://port, udp://host:port and udp-listen://port.
          - Without --duration the command runs until Ctrl+C.
          - Exit code: 0 success, 1 failure, 2 usage error.
        """.trimIndent()
    )
}

private fun Config.stringOr(path: String, fallback: String): String =
    if (hasPath(path)) getValue(path).unwrapped().toString() else fallback

private fun Config.intOr(path: String, fallback: Int): Int =
    if (hasPath(path)) getValue(path).unwrapped().toString().toIntOrNull() ?: fallback else fallback

private fun Config.stringListOr(path: String): List<String> =
    runCatching { getStringList(path) }.getOrElse {
        stringOr(path, "").split(",", ";").map(String::trim).filter(String::isNotEmpty)
    }

private fun stopBitsIndex(value: String): Int = when (value.trim().lowercase()) {
    "1.5", "one_point_five" -> 1
    "2", "two" -> 2
    else -> 0
}

private fun parityIndex(value: String): Int = when (value.trim().lowercase()) {
    "odd" -> 1
    "even" -> 2
    "mark" -> 3
    "space" -> 4
    else -> 0
}

private fun flowControlIndex(value: String): Int = when (value.trim().lowercase()) {
    "rts/cts", "rts_cts" -> 1
    "dsr/dtr", "dsr_dtr" -> 2
    "xon/xoff", "xon_xoff" -> 3
    else -> 0
}
