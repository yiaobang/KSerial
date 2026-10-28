package com.yiaobang.kserial.model

import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * 测试序列执行器：按步骤发送、等待并判定，产出逐步结果。
 *
 * 传输层是注入的，因此既可以在真实串口上跑，也可以在单元测试里用内存传输验证判定逻辑；
 * 端口开关由调用方负责（GUI 页面和 CLI 都需要自己决定连接时机）。
 */
class TestSequenceRunner(private val transport: SerialTransport) {

    class StepResult(
        val index: Int,
        val name: String,
        val passed: Boolean,
        val expected: String,
        val actual: String,
        val elapsedMs: Long
    ) {
        /** 有期望但一个字节都没收到——界面据此显示"超时"而不是"内容不符"。 */
        val timedOut: Boolean get() = !passed && actual.isEmpty() && expected.isNotEmpty()
    }

    class Report(val results: List<StepResult>, val totalMs: Long) {
        val passedCount: Int get() = results.count { it.passed }
        val failedCount: Int get() = results.size - passedCount
        val allPassed: Boolean get() = failedCount == 0
    }

    private val buffer = ByteArrayOutputStream()
    private val bufferLock = Any()

    private val dataListener: (ByteArray) -> Unit = { data ->
        synchronized(bufferLock) { buffer.write(data) }
    }

    /** 顺序执行全部步骤；[onStep] 每步结束后回调，协程取消时立即停止。 */
    suspend fun run(
        sequence: TestSequence,
        onStep: (StepResult) -> Unit = {}
    ): Report {
        transport.addDataListener(dataListener)
        val startedAt = System.currentTimeMillis()
        val results = mutableListOf<StepResult>()
        try {
            sequence.steps.forEachIndexed { index, step ->
                currentCoroutineContext().ensureActive()
                val result = runStep(index, step)
                results += result
                onStep(result)
            }
        } finally {
            transport.removeDataListener(dataListener)
        }
        return Report(results, System.currentTimeMillis() - startedAt)
    }

    private suspend fun runStep(index: Int, step: TestStep): StepResult {
        val stepStarted = System.currentTimeMillis()
        if (step.delayMs > 0) delay(step.delayMs.toLong())
        synchronized(bufferLock) { buffer.reset() }

        step.send?.let { transport.write(it) }

        val passed: Boolean
        val actual: String
        when {
            step.match == TestMatchMode.SILENCE -> {
                delay(step.timeoutMs.toLong())
                val received = snapshot()
                actual = decode(received, step)
                passed = received.isEmpty()
            }

            step.expect == null -> {
                actual = ""
                passed = true
            }

            else -> {
                val outcome = awaitExpectation(step)
                passed = outcome.first
                actual = outcome.second
            }
        }

        return StepResult(
            index = index,
            name = step.name,
            passed = passed,
            expected = step.expect ?: if (step.match == TestMatchMode.SILENCE) "silence" else "",
            actual = actual,
            elapsedMs = System.currentTimeMillis() - stepStarted
        )
    }

    private suspend fun awaitExpectation(step: TestStep): Pair<Boolean, String> {
        val raw = step.expect.orEmpty()
        val expected = if (step.encoding == TestEncoding.HEX) raw.replace(" ", "").uppercase() else raw
        var latest = ""
        val matched = withTimeoutOrNull(step.timeoutMs.toLong()) {
            while (true) {
                currentCoroutineContext().ensureActive()
                val received = snapshot()
                if (received.isNotEmpty()) {
                    latest = decode(received, step)
                    if (matches(latest, expected, step.match)) return@withTimeoutOrNull true
                }
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        return matched to latest
    }

    private fun matches(actual: String, expected: String, mode: TestMatchMode): Boolean = when (mode) {
        TestMatchMode.CONTAINS -> actual.contains(expected)
        TestMatchMode.EXACT -> actual.trim() == expected.trim()
        TestMatchMode.REGEX -> runCatching { Regex(expected).containsMatchIn(actual) }.getOrDefault(false)
        TestMatchMode.SILENCE -> false
    }

    private fun snapshot(): ByteArray = synchronized(bufferLock) { buffer.toByteArray() }

    private fun decode(bytes: ByteArray, step: TestStep): String = when (step.encoding) {
        TestEncoding.HEX -> bytes.toHexString()
        TestEncoding.ASCII -> String(bytes).trim()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 15L
    }
}
