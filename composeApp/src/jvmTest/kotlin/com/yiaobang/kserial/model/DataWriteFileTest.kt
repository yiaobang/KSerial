package com.yiaobang.kserial.model

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class DataWriteFileTest {
    @Test
    fun closeFlushesAllPendingReceiveAndSendWrites() {
        val directory = Files.createTempDirectory("kserial-data-write").toFile()
        val writer = DataWriteFile("COM_TEST", directory)

        repeat(32) { index ->
            writer.serialCommReceive(byteArrayOf(index.toByte()))
            writer.serialCommSend(byteArrayOf((255 - index).toByte()))
        }
        writer.close()

        val readFile = directory.listFiles { file -> file.extension == "read" }.single()
        val writeFile = directory.listFiles { file -> file.extension == "write" }.single()
        val expectedRead = (0 until 32).joinToString(separator = "") { "%02X ".format(it) }
        val expectedWrite = (0 until 32).joinToString(separator = "") { "%02X ".format(255 - it) }
        assertEquals(expectedRead, readFile.toPath().readText())
        assertEquals(expectedWrite, writeFile.toPath().readText())
    }
}
