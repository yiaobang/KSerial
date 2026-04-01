package com.yiaobang.kserial.model

/**
 * 定长分帧模拟器 (1:1 复刻 Java FixedLengthSimulator)
 * 适用于协议包长度固定的串口设备。
 * 
 * @param replies 指令-应答映射表
 * @param packSize 指定的每个数据包的大小
 */
class FixedLengthSimulator(replies: Map<String, ByteArray>, private val packSize: Int) : DeviceSimulator(replies) {

    /**
     * 核心检测逻辑：
     * 1. 如果缓冲区不满，则持续添加。
     * 2. 一旦达到 packSize，触发一次 reply 逻辑并清空缓冲区。
     */
    override fun checkData(bytes: ByteArray) {
        if (dataBuffer.size() + bytes.size < packSize) {
            dataBuffer.add(bytes)
        } else {
            for (b in bytes) {
                dataBuffer.add(b)
                if (dataBuffer.size() == packSize) {
                    // 当缓冲区大小正好等于 packSize 时，触发回复
                    reply(dataBuffer.getElements())
                }
            }
        }
    }
}
