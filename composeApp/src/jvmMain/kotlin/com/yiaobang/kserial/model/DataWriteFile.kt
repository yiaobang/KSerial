package com.yiaobang.kserial.model

import com.yiaobang.kserial.utils.toHexString
import kotlinx.coroutines.*
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
    private val readFile: File
    private val writeFile: File

    init {
        if (!dataDir.exists()) dataDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        readFile = File(dataDir, "$serialPortName-$timeStamp.read")
        writeFile = File(dataDir, "$serialPortName-$timeStamp.write")

        if (readFile.exists()) readFile.delete()
        if (writeFile.exists()) writeFile.delete()
    }

    /** 记录收到的数据，以 HEX 格式追加到 .read 文件 */
    fun serialCommReceive(bytes: ByteArray) {
        scope.launch {
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

    /** 记录发送的数据，以 HEX 格式追加到 .write 文件 */
    fun serialCommSend(bytes: ByteArray) {
        scope.launch {
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
