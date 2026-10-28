package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.CaptureViewModel
import com.yiaobang.kserial.feature.GatewayEndpoint
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.model.SerialPortConfig
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.utils.DesktopActions
import com.yiaobang.kserial.utils.LogExportFormat
import com.yiaobang.kserial.utils.LogExporter

/**
 * 串口录制页：左侧是双向流量日志，右侧是端口、转发与录制控制。
 * 页面本身不碰串口，全部状态在 [CaptureViewModel]。
 */
@Composable
fun CapturePage(viewModel: CaptureViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 318.dp,
            mainContent = { modifier -> CaptureLogPanel(viewModel, modifier) },
            sideContent = { CaptureControlPanel(viewModel) }
        )
        CaptureStatusBar(viewModel)
    }
}

@Composable
private fun CaptureControlPanel(vm: CaptureViewModel) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val ports by vm.availablePorts.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        CardPanel(title = strings.panelConnection) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionItem(
                    checked = !vm.manualMode,
                    label = strings.captureModeDual,
                    modifier = Modifier.weight(1f),
                    enabled = !vm.forwarding,
                    hint = strings.hintCaptureModeDual
                ) { vm.setManualCapture(false) }
                OptionItem(
                    checked = vm.manualMode,
                    label = strings.captureModeManual,
                    modifier = Modifier.weight(1f),
                    enabled = !vm.forwarding,
                    hint = strings.hintCaptureModeManual
                ) { vm.setManualCapture(true) }
            }
            if (!vm.manualMode) {
                CaptureEndpointEditor(vm, vm.softwareSide, strings.captureSideSoftware, ports)
                Spacer(Modifier.height(10.dp))
            }
            CaptureEndpointEditor(vm, vm.deviceSide, strings.captureSideDevice, ports)
        }

        CardPanel(title = if (vm.manualMode) strings.captureConnect else strings.captureStart) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (vm.manualMode) strings.captureStartManual else strings.captureStart,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    enabled = vm.canStartForwarding
                ) { vm.startForwarding() }
                ActionButton(
                    text = if (vm.manualMode) strings.captureStopManual else strings.captureStop,
                    color = colors.surface,
                    modifier = Modifier.weight(1f),
                    enabled = vm.forwarding
                ) { vm.stopForwarding() }
            }
            Text(
                text = if (vm.manualMode) strings.captureManualHint else strings.captureForwardHint,
                color = colors.onSurface.copy(alpha = 0.52f),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Text(
                text = strings.captureTraffic(formatBytes(vm.bytesToDevice), formatBytes(vm.bytesToSoftware)),
                color = colors.onSurface.copy(alpha = 0.68f),
                fontSize = 11.sp
            )
        }

        CardPanel(title = strings.panelTrafficRecorder) {
            if (vm.recording) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.error)
                    )
                    Text(
                        text = strings.capturePairs(vm.recordedPairCount),
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        fontSize = 12.sp
                    )
                    ActionButton(text = strings.captureRecordStop, color = colors.error) {
                        vm.stopRecordingAndExport()
                    }
                }
            } else {
                ActionButton(
                    text = strings.captureRecordStart,
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vm.forwarding
                ) { vm.startRecording() }
            }
            Text(
                text = strings.captureRecordHint,
                color = colors.onSurface.copy(alpha = 0.52f),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            if (vm.lastExportPath.isNotBlank()) {
                Text(
                    text = strings.captureExportPath(vm.lastExportPath),
                    color = colors.success,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun CaptureEndpointEditor(
    vm: CaptureViewModel,
    endpoint: GatewayEndpoint,
    label: String,
    ports: List<String>
) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (endpoint.isOpen) colors.success else colors.onSurface.copy(alpha = 0.24f))
            )
            Text(
                text = label,
                color = colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        ParameterInputRow(
            label = strings.captureAddress,
            value = endpoint.selectedPort,
            labelWidth = 84.dp,
            onValueChange = { if (!vm.forwarding) endpoint.selectedPort = it }
        )
        ParameterRow(
            label = strings.captureDetectedPorts,
            value = endpoint.selectedPort.ifBlank { strings.notSelected },
            options = ports,
            labelWidth = 84.dp,
            fieldHeight = 32.dp,
            onSelect = { if (!vm.forwarding) endpoint.selectedPort = it }
        )
        ParameterRow(
            label = strings.labelBaudRate,
            value = endpoint.baudRate.toString(),
            options = SerialPortConfig.BAUD_RATES.map(Int::toString),
            labelWidth = 84.dp,
            fieldHeight = 32.dp,
            onSelect = { selected -> selected.toIntOrNull()?.let { endpoint.baudRate = it } }
        )
    }
}

@Composable
private fun CaptureLogPanel(vm: CaptureViewModel, modifier: Modifier) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val listState = rememberLazyListState()
    val noHighlight = remember { KeywordHighlightConfig() }
    val visibleLogs = vm.logs
    val shape = RoundedCornerShape(10.dp)

    LaunchedEffect(visibleLogs.lastOrNull()?.id) {
        if (visibleLogs.isNotEmpty()) listState.animateScrollToItem(visibleLogs.size)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.84f), shape)
    ) {
        LogListHeader(
            totalCount = vm.logs.size,
            visibleCount = vm.logs.size,
            query = "",
            onCopyLogs = { DesktopActions.copyText(LogExporter.render(vm.logs, LogExportFormat.TEXT)) },
            onExportText = { exportCaptureLogs(vm, LogExportFormat.TEXT) },
            onExportCsv = { exportCaptureLogs(vm, LogExportFormat.CSV) },
            onClearLogs = { vm.clearLogs() }
        )
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surfaceMuted.copy(alpha = 0.56f))
        ) {
            if (visibleLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyLogState(strings.captureEmpty)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    items(visibleLogs, key = { it.id }) { log ->
                        LogEntryRow(
                            log = log,
                            baseColor = when (log.type) {
                                LogType.TX -> colors.primary
                                LogType.RX -> colors.success
                                else -> colors.onSurface.copy(alpha = 0.4f)
                            },
                            highlight = noHighlight
                        )
                    }
                }
            }
        }
        // 手动模式：日志下方就是指令输入区，本机直接扮演"软件侧"。
        if (vm.forwarding && vm.manualMode) {
            ManualSendComposer(vm)
        }
    }
}

@Composable
private fun ManualSendComposer(vm: CaptureViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inputShape = RoundedCornerShape(7.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(colors.surfaceMuted, inputShape)
                    .border(1.dp, colors.border.copy(alpha = 0.62f), inputShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = vm.manualInput,
                    onValueChange = { vm.manualInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                vm.sendManualInput()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary)
                )
                if (vm.manualInput.isEmpty()) {
                    Text(
                        text = strings.captureManualInputHint,
                        color = colors.onSurface.copy(alpha = 0.34f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ActionButton(
                text = strings.btnSendData,
                color = colors.primary,
                enabled = vm.forwarding && vm.manualInput.isNotBlank()
            ) { vm.sendManualInput() }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OptionItem(vm.manualHexMode, strings.optHexSend, hint = strings.hintHexSend) { vm.manualHexMode = it }
            ParameterRow(
                label = strings.labelLineEnding,
                value = lineEndingLabel(vm.manualLineEnding, strings),
                options = SendLineEnding.entries.map { lineEndingLabel(it, strings) },
                modifier = Modifier.width(260.dp),
                labelWidth = 48.dp,
                fieldHeight = 28.dp,
                onSelect = { selected ->
                    vm.manualLineEnding = SendLineEnding.entries.first { lineEndingLabel(it, strings) == selected }
                }
            )
        }
    }
}

@Composable
private fun CaptureStatusBar(vm: CaptureViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val labels = if (vm.manualMode) {
        listOf(strings.captureSideDevice to vm.deviceSide)
    } else {
        listOf(strings.captureSideSoftware to vm.softwareSide, strings.captureSideDevice to vm.deviceSide)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.3f)))
    ) {
        labels.forEach { (label, endpoint) ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (endpoint.isOpen) colors.success.copy(alpha = 0.12f) else colors.background)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    color = colors.onSurface.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = endpoint.selectedPort.ifBlank { strings.notSelected },
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                if (vm.recording) {
                    Text(strings.capturePairs(vm.recordedPairCount), color = colors.error, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun exportCaptureLogs(vm: CaptureViewModel, format: LogExportFormat) {
    DesktopActions.exportLogs(vm.logs, format, "KSerial")
        .onSuccess { file ->
            file?.let { vm.addLog("→ ${it.absolutePath}", LogType.INFO) }
        }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
