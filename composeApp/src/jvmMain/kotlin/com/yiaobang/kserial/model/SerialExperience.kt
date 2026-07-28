package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.FrameParser
import com.yiaobang.kserial.utils.TextEscapeParser

/** 串口接收区如何把驱动回调组合成用户可读记录。 */
enum class ReceiveDisplayMode {
    TIME_WINDOW,
    RAW_CHUNKS,
    DELIMITER,
    FIXED_LENGTH
}

/** 文本发送完成后自动追加的行尾；HEX 发送不追加行尾。 */
enum class SendLineEnding(val bytes: ByteArray) {
    NONE(byteArrayOf()),
    LF(byteArrayOf('\n'.code.toByte())),
    CR(byteArrayOf('\r'.code.toByte())),
    CRLF(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
}

/** 比单一布尔值更准确地描述连接生命周期。 */
enum class SerialConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/** 当前调制解调器输入线状态。 */
data class SerialLineSignals(
    val cts: Boolean = false,
    val dsr: Boolean = false,
    val dcd: Boolean = false,
    val ri: Boolean = false
)

/**
 * 跨驱动回调保留未完成尾部，确保分隔符和定长帧即使被拆包也能正确显示。
 * 尾部设置硬上限，错误配置或长期无分隔符时不会无限占用内存。
 */
class ReceiveFrameAssembler(
    private val maxRemainderBytes: Int = 1_048_576
) {
    private var signature: String = ""
    private var remainder = byteArrayOf()

    fun append(
        chunks: List<ByteArray>,
        mode: ReceiveDisplayMode,
        delimiterText: String,
        frameSize: Int
    ): ReceiveAssemblyResult {
        val currentSignature = "$mode|$delimiterText|$frameSize"
        if (signature != currentSignature) {
            signature = currentSignature
            remainder = byteArrayOf()
        }
        if (chunks.isEmpty()) return ReceiveAssemblyResult(emptyList())

        if (mode == ReceiveDisplayMode.RAW_CHUNKS) {
            return ReceiveAssemblyResult(chunks.map(ByteArray::copyOf))
        }

        val incoming = chunks.joinBytes()
        if (mode == ReceiveDisplayMode.TIME_WINDOW) {
            return ReceiveAssemblyResult(listOf(incoming))
        }

        val combined = remainder + incoming
        val parsed = when (mode) {
            ReceiveDisplayMode.DELIMITER -> {
                val delimiter = TextEscapeParser.decodeToByteArray(delimiterText)
                if (delimiter.isEmpty()) return ReceiveAssemblyResult(listOf(incoming), invalidConfiguration = true)
                FrameParser.delimiter(combined, delimiter)
            }
            ReceiveDisplayMode.FIXED_LENGTH -> {
                if (frameSize !in 1..4096) return ReceiveAssemblyResult(listOf(incoming), invalidConfiguration = true)
                FrameParser.fixedLength(combined, frameSize)
            }
        }

        val overflow = (parsed.remainder.size - maxRemainderBytes).coerceAtLeast(0)
        remainder = if (overflow > 0) parsed.remainder.copyOfRange(overflow, parsed.remainder.size) else parsed.remainder
        return ReceiveAssemblyResult(parsed.frames, droppedRemainderBytes = overflow.toLong())
    }

    fun reset(): ByteArray {
        val pending = remainder
        remainder = byteArrayOf()
        signature = ""
        return pending
    }

    private fun List<ByteArray>.joinBytes(): ByteArray {
        val size = sumOf(ByteArray::size)
        val result = ByteArray(size)
        var offset = 0
        forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }
}

data class ReceiveAssemblyResult(
    val frames: List<ByteArray>,
    val droppedRemainderBytes: Long = 0,
    val invalidConfiguration: Boolean = false
)
