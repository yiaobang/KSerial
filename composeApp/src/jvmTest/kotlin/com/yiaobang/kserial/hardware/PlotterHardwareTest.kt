package com.yiaobang.kserial.hardware

import com.yiaobang.kserial.feature.PlotterViewModel
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 绘图工作区的真实链路：外部端口持续发送多通道数值行，
 * 绘图页必须把它们解析成采样点；暂停后数据不再入库。
 */
class PlotterHardwareTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun streamNumericLinesAndSampleThem() {
        HardwareTestSupport.requireHardware(pairCount = 1)
        val sensorPort = HardwareTestSupport.pairs[0].first
        val plotterPort = HardwareTestSupport.pairs[0].second

        val sensor = HardwareTestSupport.open(sensorPort, baudRate = 9600)
        val viewModel = PlotterViewModel(monitorPortLoss = false)
        try {
            viewModel.port = plotterPort
            viewModel.baudRate = 9600
            viewModel.open()
            assertTrue(viewModel.isOpen, "Plotter failed to open the real port")

            // 发送 3 行采样：两通道、单通道、带标签格式各来一遍。
            val payload = "1.5,2.5\n-3.0\nTEMP=23.5,HUM=61\n".encodeToByteArray()
            var offset = 0
            // 分片发送，顺便验证解析器对跨帧粘包的处理。
            listOf(payload.copyOfRange(0, 5), payload.copyOfRange(5, 14), payload.copyOfRange(14, payload.size))
                .forEach { fragment ->
                    HardwareTestSupport.writeAndTrace(sensor, sensorPort, plotterPort, fragment)
                }

            HardwareTestSupport.eventually { viewModel.samples().size == 3 }
            val samples = viewModel.samples()
            assertEquals(3, samples.size)
            assertEquals(2, viewModel.channelCount)
            assertContentEquals(doubleArrayOf(1.5, 2.5), samples[0])
            assertContentEquals(doubleArrayOf(-3.0), samples[1])
            assertContentEquals(doubleArrayOf(23.5, 61.0), samples[2])

            // 暂停期间的数据不入库，恢复后继续。
            viewModel.togglePause()
            HardwareTestSupport.writeAndTrace(sensor, sensorPort, plotterPort, "99.0\n".encodeToByteArray())
            Thread.sleep(300)
            assertEquals(3, viewModel.samples().size, "Paused plotter must ignore incoming samples")

            viewModel.togglePause()
            HardwareTestSupport.writeAndTrace(sensor, sensorPort, plotterPort, "4.0\n".encodeToByteArray())
            HardwareTestSupport.eventually { viewModel.samples().size == 4 }
            assertContentEquals(doubleArrayOf(4.0), viewModel.samples().last())
        } finally {
            viewModel.onCleared()
            sensor.closePort()
        }
    }
}
