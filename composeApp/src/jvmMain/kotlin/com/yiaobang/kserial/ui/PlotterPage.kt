package com.yiaobang.kserial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.PlotterViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.theme.LocalAppColors

/** 曲线配色：前三条用主题语义色，其余用稳定的辅助色，深浅主题下都够对比度。 */
private val CHANNEL_COLORS = listOf(
    Color(0xFF2F6BFF),
    Color(0xFF168B63),
    Color(0xFFD84A5F),
    Color(0xFFB07CFF),
    Color(0xFFF5A623),
    Color(0xFF00A3B4),
    Color(0xFF8E5B3A),
    Color(0xFF6B7A90)
)

/** 行尾缺失警示：琥珀色在深浅主题下都可读，且不会和任何曲线通道混淆。 */
private val PENDING_COLOR = Color(0xFFB97908)

/**
 * 串口绘图页：实时把文本数值流画成曲线，用于看传感器数据、PID 调参这类场景。
 */
@Composable
fun PlotterPage(vm: PlotterViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 300.dp,
            mainContent = { modifier -> PlotterChart(vm, modifier) },
            sideContent = { PlotterControlPanel(vm) }
        )
    }
}

@Composable
private fun PlotterControlPanel(vm: PlotterViewModel) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val ports by vm.availablePorts.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        CardPanel(title = strings.plotterAddress) {
            // 地址框接受串口名或 tcp:// / udp:// 等网络地址，与终端页保持一致。
            ParameterInputRow(
                label = strings.labelAddress,
                value = vm.port,
                labelWidth = 84.dp,
                minHeight = 34.dp,
                placeholder = strings.addressPlaceholder,
                enabled = !vm.isOpen,
                onValueChange = { vm.selectPort(it.trim()) }
            )
            ParameterRow(
                label = strings.labelDetectedPorts,
                value = vm.port.ifBlank { strings.notSelected },
                options = ports,
                labelWidth = 84.dp,
                fieldHeight = 32.dp,
                onSelect = vm::selectPort
            )
            if (vm.port.contains("://")) {
                Text(
                    strings.addressNetworkHint,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.5f)
                )
            }
            ParameterRow(
                label = strings.labelBaudRate,
                value = vm.baudRate.toString(),
                options = SerialPortConfig.BAUD_RATES.map(Int::toString),
                labelWidth = 84.dp,
                onSelect = { it.toIntOrNull()?.let(vm::setBaud) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = strings.btnOpen,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    enabled = !vm.isOpen && vm.port.isNotBlank()
                ) { vm.open() }
                ActionButton(
                    text = strings.btnClose,
                    color = colors.surface,
                    modifier = Modifier.weight(1f),
                    enabled = vm.isOpen
                ) { vm.close() }
            }
        }

        CardPanel(title = strings.plotterDisplay) {
            ParameterRow(
                label = strings.plotterWindow,
                value = vm.windowSize.toString(),
                options = vm.windowOptions.map(Int::toString),
                labelWidth = 84.dp,
                onSelect = { it.toIntOrNull()?.let(vm::setWindow) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (vm.paused) strings.plotterResume else strings.plotterPause,
                    color = colors.surface,
                    modifier = Modifier.weight(1f)
                ) { vm.togglePause() }
                ActionButton(
                    text = strings.plotterClear,
                    color = colors.surface,
                    modifier = Modifier.weight(1f)
                ) { vm.clear() }
            }
            Text(
                text = strings.plotterHint,
                color = colors.onSurface.copy(alpha = 0.52f),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun PlotterChart(vm: PlotterViewModel, modifier: Modifier) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    // revision 变化才重新取一次采样副本，避免每帧都遍历缓冲。
    val revision = vm.revision
    val samples = remember(revision) { vm.samples() }
    val channels = vm.channelCount

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.84f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.plotterSamples(samples.size),
                color = colors.onSurface.copy(alpha = 0.66f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                strings.plotterChannels(channels),
                color = colors.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
            if (vm.receivedBytes > 0) {
                Text(
                    strings.plotterReceived(vm.receivedBytes),
                    color = colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            if (vm.pendingBytes > 0) {
                Text(
                    strings.plotterPending(vm.pendingBytes),
                    color = PENDING_COLOR,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.weight(1f))
            (0 until channels.coerceAtMost(CHANNEL_COLORS.size)).forEach { index ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CHANNEL_COLORS[index])
                    )
                    Text("CH${index + 1}", color = colors.onSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surfaceMuted.copy(alpha = 0.5f))
        ) {
            val chartRange = remember(samples) { valueRange(samples) }
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
                val gridColor = colors.border.copy(alpha = 0.7f)
                repeat(5) { line ->
                    val y = size.height * line / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                if (samples.isEmpty()) return@Canvas

                val min = chartRange.first
                val max = chartRange.second
                val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
                val stepX = if (samples.size > 1) size.width / (samples.size - 1) else size.width

                for (channel in 0 until channels.coerceAtMost(CHANNEL_COLORS.size)) {
                    val path = Path()
                    var started = false
                    samples.forEachIndexed { index, sample ->
                        val value = sample.getOrNull(channel) ?: return@forEachIndexed
                        val x = stepX * index
                        val y = size.height * (1f - ((value - min) / span).toFloat())
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    if (started) {
                        drawPath(
                            path = path,
                            color = CHANNEL_COLORS[channel],
                            style = Stroke(width = 1.8f)
                        )
                    }
                }
            }

            Text(
                text = formatValue(chartRange.second),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 4.dp),
                color = colors.onSurface.copy(alpha = 0.42f),
                fontSize = 10.sp
            )
            Text(
                text = formatValue(chartRange.first),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp),
                color = colors.onSurface.copy(alpha = 0.42f),
                fontSize = 10.sp
            )
            if (samples.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (vm.receivedBytes > 0) strings.plotterWaitingBytes(vm.receivedBytes)
                        else strings.plotterWaiting,
                        color = colors.onSurface.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

/** 所有通道共用一个纵轴，自动按当前窗口的最小/最大值缩放。 */
private fun valueRange(samples: List<DoubleArray>): Pair<Double, Double> {
    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    samples.forEach { sample ->
        sample.forEach { value ->
            if (value < min) min = value
            if (value > max) max = value
        }
    }
    if (min > max) return 0.0 to 1.0
    if (max - min < 1e-9) {
        val pad = if (max == 0.0) 1.0 else kotlin.math.abs(max) * 0.1
        return (min - pad) to (max + pad)
    }
    return min to max
}

private fun formatValue(value: Double): String =
    if (kotlin.math.abs(value) >= 1000 || (value != 0.0 && kotlin.math.abs(value) < 0.01)) {
        String.format("%.2e", value)
    } else {
        String.format("%.2f", value)
    }
