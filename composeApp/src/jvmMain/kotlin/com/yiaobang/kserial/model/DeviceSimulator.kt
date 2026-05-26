package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.serial.SerialComm
import com.yiaobang.kserial.utils.hexToByteArray
import com.yiaobang.kserial.utils.toUtf8ByteArray
import com.yiaobang.kserial.utils.toUtf8String
import kotlinx.serialization.json.*
import java.io.File

/**
 * 设备模拟器抽象基类
 * 负责解析自动回复配置文件，并提供指令应答的核心框架。
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
     * 静态工厂方法：根据 JSON/HOCON 配置文件创建对应的模拟器策略。
     */
    companion object {
        fun createDeviceSimulator(file: File): DeviceSimulator? {
            return try {
                if (file.extension.equals("conf", ignoreCase = true) || file.extension.equals("hocon", ignoreCase = true)) {
                    createFromConfig(file)
                } else {
                    createFromJson(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun createFromJson(file: File): DeviceSimulator? {
            val json = Json.parseToJsonElement(file.readText()).jsonObject

            val encode = json["encode"]?.jsonPrimitive?.content ?: "UTF8"
            val delimiterStr = json["delimiter"]?.jsonPrimitive?.content ?: ""
            val packSizeStr = json["packSize"]?.jsonPrimitive?.content ?: ""
            val rawMaps = json
                .filterKeys { it !in listOf("encode", "delimiter", "packSize") }
                .mapValues { it.value.jsonPrimitive.content }

            return createSimulator(encode, delimiterStr, packSizeStr, rawMaps)
        }

        private fun createFromConfig(file: File): DeviceSimulator? {
            val parsed = ConfigFactory.parseFile(file).resolve()
            val config = if (parsed.hasPath("simulator")) parsed.getConfig("simulator") else parsed
            val encode = config.optionalString("encode") ?: "UTF8"
            val delimiterStr = config.optionalString("delimiter") ?: ""
            val packSizeStr = config.optionalString("packSize") ?: ""
            val rawMaps = when {
                config.hasPath("rules") -> config.getConfigList("rules").associate { rule ->
                    rule.getString("request") to rule.getString("response")
                }
                config.hasPath("replies") -> config.getConfig("replies").entrySet().associate { entry ->
                    entry.key to entry.value.unwrapped().toString()
                }
                else -> emptyMap()
            }

            return createSimulator(encode, delimiterStr, packSizeStr, rawMaps)
        }

        private fun createSimulator(
            encode: String,
            delimiterStr: String,
            packSizeStr: String,
            rawMaps: Map<String, String>
        ): DeviceSimulator? {
            if (rawMaps.isEmpty()) return null
            val replies = mutableMapOf<String, ByteArray>()

            return if (encode.equals("HEX", ignoreCase = true)) {
                rawMaps.forEach { (k, v) ->
                    val kBytes = k.hexToByteArray()
                    val vBytes = v.hexToByteArray()
                    if (kBytes != null && vBytes != null) {
                        replies[kBytes.toUtf8String()] = vBytes
                    }
                }
                if (replies.isEmpty()) return null
                val packSize = packSizeStr.toIntOrNull()
                if (packSize != null) {
                    FixedLengthSimulator(replies, packSize)
                } else {
                    val delimiter = delimiterStr.hexToByteArray()?.takeIf { it.isNotEmpty() } ?: return null
                    DelimiterBasedSimulator(replies, delimiter)
                }
            } else {
                rawMaps.forEach { (k, v) ->
                    replies[k] = v.toUtf8ByteArray()
                }
                val packSize = packSizeStr.toIntOrNull()
                if (packSize != null) {
                    FixedLengthSimulator(replies, packSize)
                } else {
                    val delimiter = delimiterStr.toUtf8ByteArray().takeIf { it.isNotEmpty() } ?: return null
                    DelimiterBasedSimulator(replies, delimiter)
                }
            }
        }

        private fun Config.optionalString(path: String): String? {
            return if (hasPath(path)) getValue(path).unwrapped().toString() else null
        }
    }

    /** 子类实现特定的协议分帧逻辑 */
    abstract fun checkData(bytes: ByteArray)

    override fun close() {
        dataBuffer.clear()
        serialComm = null
    }
}
