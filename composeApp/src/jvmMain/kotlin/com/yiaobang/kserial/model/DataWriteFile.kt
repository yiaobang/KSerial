package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*

/**
 * 串口数据持久化记录类
 * 负责将 RX/TX 的原始数据异步写入硬盘
 */
class DataWriteFile(
    private val serialPortName: String,
    private val dataDir: File = File("serial_port_data")
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
    private val sessionId = UUID.randomUUID().toString().take(8)
    private val readFile by lazy { createSessionFile("read") }
    private val writeFile by lazy { createSessionFile("write") }
    // RX/TX 分别使用单消费者队列：既不阻塞串口回调，也保证同一方向的数据按到达顺序落盘。
    private val readQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val readJob = scope.launch { drain(readQueue, readFile) }
    private val writeJob = scope.launch { drain(writeQueue, writeFile) }

    /** 记录收到的数据，以 HEX 格式追加到 .read 文件 */
    fun serialCommReceive(bytes: ByteArray) {
        // 回调方可能复用缓冲区，入队前复制可以防止稍后写入时内容已被修改。
        readQueue.trySend(bytes.copyOf())
    }

    /** 记录发送的数据，以 HEX 格式追加到 .write 文件 */
    fun serialCommSend(bytes: ByteArray) {
        writeQueue.trySend(bytes.copyOf())
    }

    /** 停止接收新数据，并等待已入队的数据全部写完，避免会话尾部丢失。 */
    fun close() {
        readQueue.close()
        writeQueue.close()
        runBlocking {
            joinAll(readJob, writeJob)
        }
        scope.cancel()
    }

    private suspend fun drain(queue: Channel<ByteArray>, file: File) {
        for (bytes in queue) {
            // 数据记录属于辅助能力；单次磁盘写入失败不能中断串口通信或后续记录。
            runCatching {
                Files.writeString(
                    file.toPath(),
                    bytes.toHexString() + " ",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            }
        }
    }

    private fun createSessionFile(extension: String): File {
        if (!dataDir.exists()) dataDir.mkdirs()
        return File(dataDir, "$serialPortName-$timeStamp-$sessionId.$extension").also {
            if (it.exists()) it.delete()
        }
    }
}
