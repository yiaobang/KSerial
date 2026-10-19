package com.yiaobang.kserial.model

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun handlesPortNamesWithSlashes() {
        val directory = Files.createTempDirectory("kserial-data-write-slashes").toFile()
        val writer = DataWriteFile("/dev/ttyUSB0", directory)

        writer.serialCommReceive(byteArrayOf(0x01, 0x02))
        writer.serialCommSend(byteArrayOf(0x03, 0x04))
        writer.close()

        val readFile = directory.listFiles { file -> file.extension == "read" }?.singleOrNull()
        val writeFile = directory.listFiles { file -> file.extension == "write" }?.singleOrNull()
        assertNotNull(readFile)
        assertNotNull(writeFile)
        assertTrue(readFile.name.startsWith("_dev_ttyUSB0-"))
        assertTrue(writeFile.name.startsWith("_dev_ttyUSB0-"))
        assertEquals("0102 ", readFile.toPath().readText())
        assertEquals("0304 ", writeFile.toPath().readText())
    }

    @Test
    fun sanitizePortNameReplacesIllegalPathCharacters() {
        assertEquals("_dev_ttyUSB0", sanitizePortName("/dev/ttyUSB0"))
        assertEquals("__._COM10", sanitizePortName("""\\.\COM10"""))
        assertEquals("COM3", sanitizePortName("COM3"))
    }
}
