package com.yiaobang.kserial.utils

/** 已完成帧和暂不足以组成下一帧的尾部数据。 */
data class FrameParseResult(
    val frames: List<ByteArray>,
    val remainder: ByteArray
)

/** 无状态协议分帧工具，用于发送区预览和配置校验。 */
object FrameParser {
    fun fixedLength(data: ByteArray, frameSize: Int): FrameParseResult {
        require(frameSize in 1..4096) { "frameSize must be between 1 and 4096" }
        val completeBytes = data.size - (data.size % frameSize)
        val frames = (0 until completeBytes step frameSize).map { offset ->
            data.copyOfRange(offset, offset + frameSize)
        }
        return FrameParseResult(frames, data.copyOfRange(completeBytes, data.size))
    }

    fun delimiter(data: ByteArray, delimiter: ByteArray): FrameParseResult {
        require(delimiter.isNotEmpty()) { "delimiter cannot be empty" }
        require(delimiter.size <= 4096) { "delimiter cannot exceed 4096 bytes" }
        val frames = mutableListOf<ByteArray>()
        var frameStart = 0
        var index = 0
        while (index <= data.size - delimiter.size) {
            if (data.matchesAt(index, delimiter)) {
                // 返回帧包含 delimiter，便于直接对照线上真实报文。
                val end = index + delimiter.size
                frames += data.copyOfRange(frameStart, end)
                frameStart = end
                index = end
            } else {
                index++
            }
        }
        return FrameParseResult(frames, data.copyOfRange(frameStart, data.size))
    }

    private fun ByteArray.matchesAt(offset: Int, target: ByteArray): Boolean {
        if (offset < 0 || offset + target.size > size) return false
        return target.indices.all { index -> this[offset + index] == target[index] }
    }
}
