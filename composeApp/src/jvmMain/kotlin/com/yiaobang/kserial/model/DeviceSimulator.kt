package com.yiaobang.kserial.model

import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.utils.hexToByteArray
import com.yiaobang.kserial.utils.toHexString
import com.yiaobang.kserial.utils.toUtf8ByteArray
import com.yiaobang.kserial.utils.toUtf8String
import kotlinx.serialization.json.*
import java.io.File

/**
 * 设备模拟器抽象基类
 * 负责解析 JSON 配置文件，并提供指令应答的核心框架。
 */
abstract class DeviceSimulator(protected val replies: Map<String, ByteArray>) : AutoCloseable {

    /** 环形字节缓冲区，用于暂存收到的数据流 */
    protected val dataBuffer = CircularArray(4096)

    /** 绑定的串口通信实例，用于自动回复 */
    var serialComm: SerialComm? = null

    /**
     * 核心回复逻辑：根据收到的数据，在应答表中查找并发送对应回复
     */
    protected fun reply(bytes: ByteArray) {
        val request = bytes.toUtf8String()
        val replyData = replies[request]
        if (replyData != null) {
            serialComm?.write(replyData)
        }
        dataBuffer.clear()
    }

    /**
     * 静态工厂方法：根据 JSON 配置文件创建对应的模拟器策略
     */
    companion object {
        fun createDeviceSimulator(file: File): DeviceSimulator? {
            return try {
                val json = Json.parseToJsonElement(file.readText()).jsonObject

                val encode = json["encode"]?.jsonPrimitive?.content ?: "UTF8"
                val delimiterStr = json["delimiter"]?.jsonPrimitive?.content ?: ""
                val packSizeStr = json["packSize"]?.jsonPrimitive?.content ?: ""

                val rawMaps = json.filterKeys { it !in listOf("encode", "delimiter", "packSize") }
                val replies = mutableMapOf<String, ByteArray>()

                if (encode.equals("HEX", ignoreCase = true)) {
                    rawMaps.forEach { (k, v) ->
                        val kBytes = k.hexToByteArray()
                        val vBytes = v.jsonPrimitive.content.hexToByteArray()
                        if (kBytes != null && vBytes != null) {
                            replies[kBytes.toUtf8String()] = vBytes
                        }
                    }
                    val packSize = packSizeStr.toIntOrNull()
                    if (packSize != null) {
                        FixedLengthSimulator(replies, packSize)
                    } else {
                        val delimiter = delimiterStr.hexToByteArray() ?: return null
                        DelimiterBasedSimulator(replies, delimiter)
                    }
                } else {
                    rawMaps.forEach { (k, v) ->
                        replies[k] = v.jsonPrimitive.content.toUtf8ByteArray()
                    }
                    val packSize = packSizeStr.toIntOrNull()
                    if (packSize != null) {
                        FixedLengthSimulator(replies, packSize)
                    } else {
                        DelimiterBasedSimulator(replies, delimiterStr.toUtf8ByteArray())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /** 子类实现特定的协议分帧逻辑 */
    abstract fun checkData(bytes: ByteArray)

    override fun close() {
        dataBuffer.clear()
        serialComm = null
    }
}
