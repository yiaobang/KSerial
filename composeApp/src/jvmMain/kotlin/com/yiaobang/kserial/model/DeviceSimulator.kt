package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.serial.SerialTransport
import com.yiaobang.kserial.utils.hexToByteArray
import com.yiaobang.kserial.utils.toHexString
import com.yiaobang.kserial.utils.toUtf8ByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 应答延迟：固定毫秒数，或区间内随机——真设备的处理耗时有抖动，固定值反而假。 */
class ReplyDelay(val minMs: Long, val maxMs: Long) {
    init {
        require(minMs in 0..MAX_DELAY_MS) { "minMs out of range: $minMs" }
        require(maxMs in minMs..MAX_DELAY_MS) { "maxMs out of range: $maxMs" }
    }

    /** 取一次实际延迟；固定值直接返回，区间值均匀随机。 */
    fun sample(): Long =
        if (maxMs == minMs) minMs else minMs + kotlin.random.Random.nextLong(maxMs - minMs + 1)

    companion object {
        const val MAX_DELAY_MS = 600_000L

        fun fixed(ms: Long): ReplyDelay = ReplyDelay(ms, ms)

        /** 解析 "300" / "100~300" / "100-300"；非法或区间倒置返回 null。 */
        fun parse(raw: String): ReplyDelay? {
            val trimmed = raw.trim()
            trimmed.toLongOrNull()?.let { ms ->
                return if (ms in 0..MAX_DELAY_MS) fixed(ms) else null
            }
            val match = Regex("""(\d+)\s*[~\-]\s*(\d+)""").find(trimmed) ?: return null
            val lo = match.groupValues[1].toLong()
            val hi = match.groupValues[2].toLong()
            if (lo !in 0..MAX_DELAY_MS || hi !in lo..MAX_DELAY_MS) return null
            return ReplyDelay(lo, hi)
        }
    }
}

/**
 * 设备模拟器抽象基类
 * 负责解析自动回复配置文件，并提供指令应答的核心框架。
 */
abstract class DeviceSimulator(
    protected val replies: Map<String, ByteArray>,
    /** 每条规则的应答延迟，key 与 replies 一致；未指定的规则用 defaultDelay。 */
    private val ruleDelays: Map<String, ReplyDelay> = emptyMap(),
    /** 规则未单独指定延迟时使用的全局延迟。 */
    private val defaultDelay: ReplyDelay = ReplyDelay.fixed(0)
) : AutoCloseable {

    /** 环形字节缓冲区，用于暂存收到的数据流 */
    protected val dataBuffer = CircularArray(4096)

    /** 绑定的串口通信实例，用于自动回复 */
    var serialComm: SerialTransport? = null

    /** 自动回复完成回调，用于同步页面日志、计数和数据落盘状态 */
    var onReply: ((data: ByteArray, written: Int) -> Unit)? = null

    /**
     * 应答协程作用域，由 ViewModel 注入。
     * null（测试或旧调用路径）时保持当拍同步应答，行为与旧版本一致；
     * 注入后，带延迟的应答经由单消费者通道排队串行发出——真设备一次只处理一条请求，
     * 多条延迟应答必须保持 FIFO 顺序。
     */
    var replyScope: CoroutineScope? = null

    private data class PendingReply(val data: ByteArray, val delayMs: Long)

    private val replyQueue = Channel<PendingReply>(Channel.UNLIMITED)
    private val workerLock = Any()
    private var replyWorker: Job? = null

    /**
     * 核心回复逻辑：根据收到的数据，在应答表中查找并发出对应回复。
     * 延迟为 0 时同步发出；否则交给应答通道，由工作协程延迟后串行发送。
     */
    protected fun reply(bytes: ByteArray) {
        val request = bytes.toHexString()
        val replyData = replies[request]
        if (replyData != null) {
            val delayMs = (ruleDelays[request] ?: defaultDelay).sample().coerceIn(0, ReplyDelay.MAX_DELAY_MS)
            dispatchReply(PendingReply(replyData, delayMs))
        }
        dataBuffer.clear()
    }

    private fun dispatchReply(task: PendingReply) {
        val scope = replyScope
        if (scope == null || task.delayMs <= 0L) {
            val written = serialComm?.write(task.data) ?: -1
            onReply?.invoke(task.data, written)
            return
        }
        ensureReplyWorker(scope)
        replyQueue.trySend(task)
    }

    private fun ensureReplyWorker(scope: CoroutineScope) {
        synchronized(workerLock) {
            if (replyWorker == null) {
                replyWorker = scope.launch {
                    for (task in replyQueue) {
                        if (task.delayMs > 0) delay(task.delayMs)
                        val written = withContext(Dispatchers.IO) { serialComm?.write(task.data) ?: -1 }
                        onReply?.invoke(task.data, written)
                    }
                }
            }
        }
    }

    /** 静态工厂方法：根据已经通过校验的 HOCON 配置创建模拟器策略。 */
    companion object {
        fun createDeviceSimulator(file: File): DeviceSimulator? {
            if (!file.extension.equals("conf", ignoreCase = true) &&
                !file.extension.equals("hocon", ignoreCase = true)
            ) return null
            return try {
                createFromConfig(file)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun createFromConfig(file: File): DeviceSimulator? {
            val parsed = ConfigFactory.parseFile(file).resolve()
            val config = if (parsed.hasPath("simulator")) parsed.getConfig("simulator") else parsed
            val encode = config.optionalString("encode") ?: "UTF8"
            val delimiterStr = config.optionalString("delimiter") ?: ""
            val packSizeStr = config.optionalString("packSize") ?: ""
            val defaultDelay = config.optionalString("responseDelay")
                ?.let { ReplyDelay.parse(it) } ?: ReplyDelay.fixed(0)
            val rawRules = if (config.hasPath("rules")) {
                config.getConfigList("rules").map { rule ->
                    Triple(
                        rule.getString("request"),
                        rule.getString("response"),
                        rule.optionalString("delay")?.let { ReplyDelay.parse(it) }
                    )
                }
            } else emptyList()

            return createSimulator(encode, delimiterStr, packSizeStr, rawRules, defaultDelay)
        }

        private fun createSimulator(
            encode: String,
            delimiterStr: String,
            packSizeStr: String,
            rawRules: List<Triple<String, String, ReplyDelay?>>,
            defaultDelay: ReplyDelay
        ): DeviceSimulator? {
            if (rawRules.isEmpty()) return null
            val isHex = encode.equals("HEX", ignoreCase = true)
            val replies = mutableMapOf<String, ByteArray>()
            val delays = mutableMapOf<String, ReplyDelay>()

            // 请求/应答/延迟统一做 key 归一化，保证匹配与延迟查找使用同一套键。
            rawRules.forEach { (request, response, delay) ->
                val key = if (isHex) {
                    request.hexToByteArray()?.toHexString() ?: return@forEach
                } else {
                    request.toUtf8ByteArray().toHexString()
                }
                val value = if (isHex) {
                    response.hexToByteArray() ?: return@forEach
                } else {
                    response.toUtf8ByteArray()
                }
                replies[key] = value
                delay?.let { delays[key] = it }
            }
            if (replies.isEmpty()) return null

            val packSize = packSizeStr.toIntOrNull()?.takeIf { it in 1..4096 }
            return if (packSize != null) {
                FixedLengthSimulator(replies, packSize, delays, defaultDelay)
            } else {
                val delimiter = if (isHex) {
                    delimiterStr.hexToByteArray()?.takeIf { it.isNotEmpty() && it.size <= 4096 }
                } else {
                    delimiterStr.toUtf8ByteArray().takeIf { it.isNotEmpty() && it.size <= 4096 }
                } ?: return null
                DelimiterBasedSimulator(replies, delimiter, delays, defaultDelay)
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
        // 关闭通道并取消工作协程，挂起中的延迟应答随端口关闭一起作废。
        replyQueue.close()
        replyWorker?.cancel()
        replyWorker = null
        serialComm = null
        onReply = null
    }
}
