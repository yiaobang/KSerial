package com.yiaobang.kserial.utils

import com.yiaobang.kserial.model.DeviceSimulator
import com.yiaobang.kserial.model.TrafficRecorder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulatorConfigGeneratorTest {
    private fun pair(request: String, response: String) =
        TrafficRecorder.TrafficPair(request.encodeToByteArray(), response.encodeToByteArray())

    @Test
    fun textPairsDetectCrLfDelimiterAndRoundTrip() {
        val config = SimulatorConfigGenerator.generate(
            listOf(pair("AT\r\n", "OK\r\n"), pair("PING\r\n", "PONG\r\n")),
            endpoint = null
        )
        assertTrue(config.contains("encode = \"ASCII\""))
        assertTrue(config.contains("delimiter = \"\\r\\n\""))
        assertLoadsAndReplays(
            config,
            "PING\r\n".encodeToByteArray(),
            "PONG\r\n".encodeToByteArray()
        )
    }

    @Test
    fun binaryPairsSwitchToHexAndRoundTrip() {
        val request = byteArrayOf(0x01, 0x03, 0x00)
        val response = byteArrayOf(0x01, 0x03, 0x2A)
        val config = SimulatorConfigGenerator.generate(
            listOf(TrafficRecorder.TrafficPair(request, response)),
            endpoint = null
        )
        assertTrue(config.contains("encode = \"HEX\""))
        assertLoadsAndReplays(config, request, response)
    }

    @Test
    fun uniformLengthRequestsFallBackToPackSize() {
        // 无统一帧尾，但请求等长 → packSize 分帧
        val request = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val response = byteArrayOf(0x55, 0x66, 0x77, 0x88.toByte())
        val config = SimulatorConfigGenerator.generate(
            listOf(TrafficRecorder.TrafficPair(request, response)),
            endpoint = null
        )
        assertTrue(config.contains("packSize = 4"))
        assertLoadsAndReplays(config, request, response)
    }

    @Test
    fun generatedConfigCarriesEndpointSettings() {
        val endpoint = SimulatorConfigGenerator.EndpointInfo(
            port = "COM9",
            baudRate = 115200,
            dataBits = 8,
            stopBitsIndex = 0,
            parityIndex = 0,
            flowControlIndex = 0
        )
        val config = SimulatorConfigGenerator.generate(listOf(pair("AT\r\n", "OK\r\n")), endpoint)
        assertTrue(config.contains("port = \"COM9\""))
        assertTrue(config.contains("baudRate = 115200"))
        assertTrue(config.contains("parity = \"none\""))
    }

    @Test
    fun mixedPrintableAndControlBytesRemainText() {
        // \r\n 是控制字节但属于可打印文本范畴，不应误判为 HEX
        val config = SimulatorConfigGenerator.generate(
            listOf(pair("AT+VERSION\r\n", "KSerial 1.0\r\n")),
            endpoint = null
        )
        assertTrue(config.contains("encode = \"ASCII\""))
    }

    private fun assertLoadsAndReplays(config: String, request: ByteArray, expected: ByteArray) {
        val file = Files.createTempFile("kserial-generated", ".conf").toFile()
        try {
            file.writeText(config)
            val simulator = DeviceSimulator.createDeviceSimulator(file)
            assertNotNull(simulator, "生成的配置应能被 DeviceSimulator 加载")
            val replies = mutableListOf<ByteArray>()
            simulator.onReply = { data, _ -> replies += data }
            simulator.checkData(request)
            assertEquals(1, replies.size)
            assertContentEquals(expected, replies.single())
        } finally {
            file.delete()
        }
    }
}
