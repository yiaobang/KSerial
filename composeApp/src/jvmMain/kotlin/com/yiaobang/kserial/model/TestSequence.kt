package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.utils.hexToByteArray
import java.io.File

/** 单步的判定方式。 */
enum class TestMatchMode {
    /** 收到内容里包含期望串（默认，最适合"OK"/"ERROR:xx"这类应答）。 */
    CONTAINS,
    EXACT,
    REGEX,

    /** 静默检查：在超时窗口内不应收到任何字节。 */
    SILENCE
}

/** 步骤里字节的书写方式：ASCII 直接写文本，HEX 写 "0D 0A" 这类字节串。 */
enum class TestEncoding {
    ASCII,
    HEX
}

/**
 * 一个测试步骤。
 *
 * [send] 为空表示只等待；[expect] 为空表示只发送不校验。
 * [encoding] 同时作用于 send 的解析和 expect 的比较口径（HEX 模式按规范化 hex 串比较）。
 */
class TestStep(
    val name: String,
    val send: ByteArray?,
    val expect: String?,
    val match: TestMatchMode,
    val timeoutMs: Int,
    val delayMs: Int,
    val encoding: TestEncoding
)

/** 配置文件里 connection 段的连接参数。 */
class TestConnection(
    val port: String,
    val baudRate: Int,
    val dataBits: Int,
    val stopBitsIndex: Int,
    val parityIndex: Int,
    val flowControlIndex: Int
)

/**
 * 自动化测试序列：把"发指令、等应答"的串口回归动作描述成可重复执行的步骤表。
 *
 * 典型配置：
 * ```hocon
 * test {
 *   connection { port = "COM2", baudRate = 115200 }
 *   defaults { timeout = 1000, interval = 100 }
 *   steps = [
 *     { name = "握手", send = "AT\r\n", expect = "OK", timeout = 800 },
 *     { name = "版本", send = "AT+VERSION\r\n", expect = "KSerial" },
 *     { name = "静默", match = "silence", timeout = 300 }
 *   ]
 * }
 * ```
 */
class TestSequence(
    val connection: TestConnection,
    val defaultTimeoutMs: Int,
    val intervalMs: Int,
    val steps: List<TestStep>
) {
    companion object {
        private const val MAX_TIMEOUT_MS = 600_000
        private const val MAX_STEPS = 500

        /** 读取并解析测试配置；格式问题时抛出 [IllegalArgumentException]，消息可直接展示。 */
        fun load(file: File): TestSequence {
            val config = ConfigFactory.parseFile(file).resolve()
            val root = when {
                config.hasPath("test") -> config.getConfig("test")
                else -> config
            }
            if (!root.hasPath("steps")) throw IllegalArgumentException("test.steps is required")

            val connectionConfig = if (root.hasPath("connection")) root.getConfig("connection") else null
            val defaults = if (root.hasPath("defaults")) root.getConfig("defaults") else null
            val defaultTimeout = (defaults?.intOr("timeout", 1_000) ?: 1_000).coerceIn(1, MAX_TIMEOUT_MS)
            val interval = (defaults?.intOr("interval", 100) ?: 100).coerceIn(0, 10_000)

            val connection = TestConnection(
                port = connectionConfig?.stringOr("port", "") ?: "",
                baudRate = connectionConfig?.intOr("baudRate", 115_200) ?: 115_200,
                dataBits = connectionConfig?.intOr("dataBits", 8) ?: 8,
                stopBitsIndex = stopBitsIndex(connectionConfig?.stringOr("stopBits", "1") ?: "1"),
                parityIndex = parityIndex(connectionConfig?.stringOr("parity", "none") ?: "none"),
                flowControlIndex = flowControlIndex(connectionConfig?.stringOr("flowControl", "none") ?: "none")
            )

            val steps = root.getConfigList("steps").mapIndexed { index, step ->
                val encoding = when (step.stringOr("encoding", "ascii").trim().lowercase()) {
                    "hex" -> TestEncoding.HEX
                    else -> TestEncoding.ASCII
                }
                val match = when (step.stringOr("match", "contains").trim().lowercase()) {
                    "exact" -> TestMatchMode.EXACT
                    "regex" -> TestMatchMode.REGEX
                    "silence" -> TestMatchMode.SILENCE
                    else -> TestMatchMode.CONTAINS
                }
                val sendText = step.stringOrNull("send")
                val send = when {
                    sendText.isNullOrEmpty() -> null
                    encoding == TestEncoding.HEX -> sendText.hexToByteArray()
                        ?: throw IllegalArgumentException("step ${index + 1}: send is not valid HEX")
                    else -> sendText.encodeToByteArray()
                }
                val expect = when (match) {
                    TestMatchMode.SILENCE -> null
                    else -> step.stringOrNull("expect")?.takeIf { it.isNotEmpty() }
                }
                if (match == TestMatchMode.REGEX && expect != null) {
                    runCatching { Regex(expect) }.onFailure {
                        throw IllegalArgumentException("step ${index + 1}: invalid regex \"$expect\"")
                    }
                }
                if (send == null && expect == null && match != TestMatchMode.SILENCE) {
                    throw IllegalArgumentException("step ${index + 1}: needs send, expect or match = \"silence\"")
                }
                TestStep(
                    name = step.stringOrNull("name") ?: "Step ${index + 1}",
                    send = send,
                    expect = expect,
                    match = match,
                    timeoutMs = step.intOr("timeout", defaultTimeout).coerceIn(1, MAX_TIMEOUT_MS),
                    delayMs = step.intOr("delay", interval).coerceIn(0, 10_000),
                    encoding = encoding
                )
            }
            if (steps.isEmpty()) throw IllegalArgumentException("test.steps must not be empty")
            require(steps.size <= MAX_STEPS) { "at most $MAX_STEPS steps are supported" }

            return TestSequence(connection, defaultTimeout, interval, steps)
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

        private fun Config.stringOr(path: String, fallback: String): String = stringOrNull(path) ?: fallback

        private fun Config.stringOrNull(path: String): String? =
            if (hasPath(path)) getValue(path).unwrapped().toString() else null

        private fun Config.intOr(path: String, fallback: Int): Int =
            if (hasPath(path)) getValue(path).unwrapped().toString().toIntOrNull() ?: fallback else fallback
    }
}
