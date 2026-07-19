package com.yiaobang.kserial.hardware

import com.fazecast.jSerialComm.SerialPort
import com.yiaobang.kserial.serial.SerialComm
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

internal object HardwareTestSupport {
    // 每对端口的 first 是外部测试端，second 是被测终端/模拟器/桥接实际打开的端口。
    private val defaultPairs = listOf(
        "COM1" to "COM2",
        "COM3" to "COM4",
        "COM5" to "COM6",
        "COM7" to "COM8",
        "COM9" to "COM10"
    )

    val pairs: List<Pair<String, String>> by lazy {
        // CI 或其他机器可用 COM1:COM2,COM3:COM4 格式覆盖默认拓扑，无需修改测试代码。
        val configured = System.getProperty("kserial.pairs")
            ?: System.getenv("KSERIAL_PORT_PAIRS")
        configured
            ?.split(',')
            ?.mapNotNull { token ->
                val ports = token.split(':', limit = 2).map(String::trim)
                ports.takeIf { it.size == 2 && it.all(String::isNotEmpty) }?.let { it[0] to it[1] }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultPairs
    }

    fun requireHardware(pairCount: Int) {
        // 普通 jvmTest 必须跳过真实硬件；只有 IDE 中专用的 HardwareTest 任务会打开串口。
        val enabled = System.getProperty("kserial.hardwareTests").toBoolean() ||
            System.getenv("KSERIAL_HARDWARE_TESTS").toBoolean()
        assumeTrue("Hardware tests are disabled; run a dedicated *HardwareTest Gradle task.", enabled)
        assumeTrue("At least $pairCount configured virtual-port pairs are required.", pairs.size >= pairCount)

        val available = SerialPort.getCommPorts().map { it.systemPortName.uppercase() }.toSet()
        val required = pairs.take(pairCount).flatMap { listOf(it.first.uppercase(), it.second.uppercase()) }
        assumeTrue("Missing virtual ports: ${required.filterNot(available::contains)}", required.all(available::contains))
    }

    fun open(
        portName: String,
        baudRate: Int = 115200,
        dataBits: Int = 8,
        stopBitsIndex: Int = 0,
        parityIndex: Int = 0,
        flowControlIndex: Int = 0
    ): SerialComm = SerialComm().also { comm ->
        trace("OPEN $portName @ $baudRate baud, $dataBits data bits")
        assertTrue(
            comm.openPort(portName, baudRate, dataBits, stopBitsIndex, parityIndex, flowControlIndex),
            "Unable to open $portName: ${comm.lastErrorMessage}"
        )
        trace("OPENED $portName")
    }

    fun writeAndTrace(
        comm: SerialComm,
        sourcePort: String,
        destination: String,
        payload: ByteArray
    ): Int {
        // 可选暂停只用于人工观察，不参与收发正确性的判断。
        observationPause()
        trace("SEND $sourcePort -> $destination: ${payload.size} bytes [${payload.preview()}]")
        val written = comm.write(payload)
        trace("WRITE RESULT $sourcePort: $written/${payload.size} bytes")
        observationPause()
        return written
    }

    fun traceReceived(destination: String, payload: ByteArray) {
        trace("RECEIVED $destination: ${payload.size} bytes [${payload.preview()}]")
    }

    fun tempConfig(prefix: String, content: String): Path =
        Files.createTempFile(prefix, ".conf").also { Files.writeString(it, content.trimIndent()) }

    fun eventually(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue(condition(), "Condition was not met within ${timeoutMillis}ms")
    }

    private fun observationPause() {
        val delayMillis = System.getProperty("kserial.hardwareObservationDelayMs")
            ?.toLongOrNull()
            ?.coerceIn(0L, 5_000L)
            ?: 0L
        if (delayMillis > 0) Thread.sleep(delayMillis)
    }

    private fun trace(message: String) {
        println("[KSerial-HW] $message")
    }

    private fun ByteArray.preview(): String =
        take(16).joinToString(" ") { "%02X".format(it.toUByte().toInt()) } +
            if (size > 16) " ..." else ""
}

internal class SerialCapture(expectedSize: Int) {
    private val expectedSize = expectedSize
    private val bytes = ByteArrayOutputStream(expectedSize)
    private val latch = CountDownLatch(1)
    // 串口驱动可能把一帧拆成多次回调，因此累积到期望长度后再释放断言线程。
    val listener: (ByteArray) -> Unit = { chunk ->
        synchronized(bytes) {
            bytes.write(chunk)
            if (bytes.size() >= expectedSize) latch.countDown()
        }
    }

    fun await(timeoutMillis: Long = 5_000): ByteArray {
        assertTrue(latch.await(timeoutMillis, TimeUnit.MILLISECONDS), "Expected $expectedSize bytes were not received")
        return synchronized(bytes) { bytes.toByteArray() }
    }

    fun receivedWithin(timeoutMillis: Long): Boolean = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
}
