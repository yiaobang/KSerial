package com.yiaobang.kserial.model

/**
 * 基于分隔符的模拟器 (1:1 复刻 Java DelimiterBasedSimulator)
 * 适用于以特定字符（如 \n, \r\n）结尾的文本型协议。
 * 
 * @param replies 指令-应答映射表
 * @param delimiter 预设的匹配结束符，例如 ASCII 的 0x0A (LF)
 */
class DelimiterBasedSimulator(replies: Map<String, ByteArray>, private val delimiter: ByteArray) : DeviceSimulator(replies) {

    /**
     * 核心检测逻辑：
     * 1. 将接收到的每个字节依次推入缓冲区。
     * 2. 检查缓冲区末尾是否与 delimiter (结束符) 一致。
     */
    override fun checkData(bytes: ByteArray) {
        for (b in bytes) {
            dataBuffer.add(b)
            if (checkForEndDelimiter()) {
                // 如果检测到结束符成功，触发回复并根据 reply 内部逻辑清空缓冲区
                reply(dataBuffer.getElements())
            }
        }
    }

    /**
     * 后缀匹配检查算法：
     * 比较缓冲区末尾的几个字节是否有结束符序列相等。
     */
    private fun checkForEndDelimiter(): Boolean {
        if (dataBuffer.size() < delimiter.size) {
            return false // 数据量不够，不可能包含结束符
        }
        
        // 从后面往前比对
        for (i in delimiter.indices) {
            if (dataBuffer.getElement(dataBuffer.size() - delimiter.size + i) != delimiter[i]) {
                return false
            }
        }
        return true
    }
}
