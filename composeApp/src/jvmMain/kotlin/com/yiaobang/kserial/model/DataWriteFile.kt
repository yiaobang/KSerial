package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*

/**
 * 串口数据持久化记录类
 * 负责将 RX/TX 的原始数据异步写入硬盘
 */
class DataWriteFile(private val serialPortName: String) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataDir = File("serial_port_data")
    private val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
    private val sessionId = UUID.randomUUID().toString().take(8)
    private val readFile by lazy { createSessionFile("read") }
    private val writeFile by lazy { createSessionFile("write") }
    private val readMutex = Mutex()
    private val writeMutex = Mutex()

    /** 记录收到的数据，以 HEX 格式追加到 .read 文件 */
    fun serialCommReceive(bytes: ByteArray) {
        scope.launch {
            readMutex.withLock {
                runCatching {
                    Files.writeString(
                        readFile.toPath(),
                        bytes.toHexString() + " ",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    )
                }
            }
        }
    }

    /** 记录发送的数据，以 HEX 格式追加到 .write 文件 */
    fun serialCommSend(bytes: ByteArray) {
        scope.launch {
            writeMutex.withLock {
                runCatching {
                    Files.writeString(
                        writeFile.toPath(),
                        bytes.toHexString() + " ",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    )
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun createSessionFile(extension: String): File {
        if (!dataDir.exists()) dataDir.mkdirs()
        return File(dataDir, "$serialPortName-$timeStamp-$sessionId.$extension").also {
            if (it.exists()) it.delete()
        }
    }
}
