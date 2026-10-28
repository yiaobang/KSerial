package com.yiaobang.kserial.model

/**
 * 终端流量录制器：按时间顺序记录收发事件，停止后按
 * "一次 TX 及其后到下一个 TX 之前的全部 RX" 配对成问答，供模拟器配置生成。
 *
 * 线程安全：TX 在发送协程记录，RX 在驱动回调线程记录，内部用锁串行化。
 * 配对假设：设备总是在收到请求之后才应答，因此请求之后、下一次请求之前的
 * 所有接收字节都属于该请求的应答（串口是全双工但问答式设备天然满足）。
 */
class TrafficRecorder {

    /** 一次问答：请求与其后收到的全部响应字节拼接。 */
    class TrafficPair(val request: ByteArray, val response: ByteArray)

    private class Event(val isTx: Boolean, val bytes: ByteArray)

    private val events = mutableListOf<Event>()
    private val lock = Any()

    fun onTx(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(lock) { events += Event(true, bytes.copyOf()) }
    }

    fun onRx(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(lock) { events += Event(false, bytes.copyOf()) }
    }

    /** 当前已捕获的问答组数；只在 TX 到达时变化，UI 可在 onTx 后刷新。 */
    fun pairCount(): Int = pairs().size

    fun clear() = synchronized(lock) { events.clear() }

    /**
     * 配对规则：
     * - TX 与其后（下一个 TX 之前）的全部 RX 拼接为应答；
     * - 无应答的请求丢弃（配不出规则）；
     * - 应答与请求完全相同的丢弃（回环/自发自收）。
     * 相同请求出现多次时后者覆盖前者（模拟器规则表本身就是覆盖语义）。
     */
    fun pairs(): List<TrafficPair> = synchronized(lock) {
        val result = mutableListOf<TrafficPair>()
        var i = 0
        while (i < events.size) {
            val event = events[i]
            if (!event.isTx) {
                i++
                continue
            }
            var j = i + 1
            var responseSize = 0
            while (j < events.size && !events[j].isTx) {
                responseSize += events[j].bytes.size
                j++
            }
            if (responseSize > 0) {
                val response = ByteArray(responseSize)
                var offset = 0
                for (k in i + 1 until j) {
                    events[k].bytes.copyInto(response, offset)
                    offset += events[k].bytes.size
                }
                val request = event.bytes
                if (!response.contentEquals(request)) {
                    result += TrafficPair(request, response)
                }
            }
            i = j
        }
        result
    }
}
