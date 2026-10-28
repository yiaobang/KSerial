package com.yiaobang.kserial.model

/**
 * 数值流解析：把串口文本按行切分并抽出每行的数字列，供绘图页逐列画曲线。
 *
 * 刻意用"抽取数字"而不是严格按分隔符 split：现场数据常见 `TEMP=23.5,HUM=61`
 * 这种带标签的格式，抽数字能同时兼容纯 CSV、空白分隔和带前缀的打印语句。
 */
class NumericStreamParser(
    /** 每行最多保留的通道数，超出的列直接忽略。 */
    private val maxChannels: Int = 8,
    /** 单行长度上限，防止没有换行的二进制流把缓冲撑爆。 */
    private val maxLineLength: Int = 4_096
) {
    private val lineBuffer = StringBuilder()
    private val numberPattern = Regex("[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")

    /** 还没等到换行符、暂存在行缓冲里的字节数；用于诊断"数据在到达但缺行尾"。 */
    val pendingSize: Int get() = lineBuffer.length

    /** 喂入原始字节，返回其中完整行解析出的样本（每行一个 DoubleArray）。 */
    fun feed(chunk: ByteArray): List<DoubleArray> {
        val text = String(chunk)
        val results = mutableListOf<DoubleArray>()
        text.forEach { ch ->
            when {
                ch == '\n' -> {
                    parseLine(lineBuffer.toString())?.let(results::add)
                    lineBuffer.setLength(0)
                }

                ch == '\r' -> Unit

                lineBuffer.length >= maxLineLength -> lineBuffer.setLength(0)

                else -> lineBuffer.append(ch)
            }
        }
        return results
    }

    fun reset() {
        lineBuffer.setLength(0)
    }

    private fun parseLine(line: String): DoubleArray? {
        if (line.isBlank()) return null
        val values = numberPattern.findAll(line)
            .mapNotNull { it.value.toDoubleOrNull() }
            .take(maxChannels)
            .toList()
        return values.takeIf { it.isNotEmpty() }?.toDoubleArray()
    }

    companion object {
        /** 内置演示/测试用的简单解析：只按逗号或空白切分，不做数字抽取。 */
        fun splitSimple(line: String, maxChannels: Int = 8): DoubleArray? {
            val parts = line.trim().split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val values = parts.mapNotNull { it.toDoubleOrNull() }.take(maxChannels)
            return values.takeIf { it.isNotEmpty() }?.toDoubleArray()
        }
    }
}
