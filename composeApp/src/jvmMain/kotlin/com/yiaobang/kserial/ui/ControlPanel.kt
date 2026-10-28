package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.ReceiveDisplayMode
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.model.SerialConnectionState
import com.yiaobang.kserial.theme.LocalAppColors

/**
 * 右侧控制面板容器，包含连接参数、动作按钮、选项复选框三张卡片。
 * 表单基础控件（CardPanel、ParameterRow 等）见 CommonControls.kt。
 * 日志相关的关键词高亮与筛选已收进日志头部（见 LogWidgets.kt），不在此处重复。
 */
@Composable
fun ControlPanel(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val config = vm.config
    var advancedConnectionExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 连接参数配置卡片
        CardPanel(title = s.panelConnection) {
            ConnectionFeedback(vm)
            PortParameterRow(vm)
            ParameterRow(s.labelBaudRate, config.baudRate.toString(), com.yiaobang.kserial.model.SerialPortConfig.BAUD_RATES.map { it.toString() }, labelWidth = 84.dp) { config.baudRate = it.toInt() }
            ExpandableSectionToggle(
                title = s.labelAdvancedSerialParameters,
                expanded = advancedConnectionExpanded,
                onToggle = { advancedConnectionExpanded = !advancedConnectionExpanded }
            )
            if (advancedConnectionExpanded) {
                ParameterRow(s.labelDataBits, config.dataBits.toString(), com.yiaobang.kserial.model.SerialPortConfig.DATA_BITS_LIST.map { it.toString() }, labelWidth = 84.dp) { config.dataBits = it.toInt() }
                ParameterRow(s.labelStopBits, s.stopBitsOptions[config.stopBitsIndex], s.stopBitsOptions, labelWidth = 84.dp) { config.stopBitsIndex = s.stopBitsOptions.indexOf(it) }
                ParameterRow(s.labelParity, s.parityOptions[config.parityIndex], s.parityOptions, labelWidth = 84.dp) { config.parityIndex = s.parityOptions.indexOf(it) }
                ParameterRow(s.labelFlowControl, s.flowControlOptions[config.flowControlIndex], s.flowControlOptions, labelWidth = 84.dp) { config.flowControlIndex = s.flowControlOptions.indexOf(it) }
            }
        }

        // 动作按钮卡片
        CardPanel(title = s.panelActions) {
            ActionButtonGrid(vm = vm, onClearSend = { vm.inputText = "" })
        }

        ExpandableCardPanel(title = s.panelOptions) {
            OptionsGrid(vm = vm)
        }

        ExpandableCardPanel(title = s.panelReceiveDisplay) {
            ReceiveDisplayPanel(vm)
        }

        ExpandableCardPanel(title = s.panelLineControl) {
            LineControlPanel(vm)
        }

        ExpandableCardPanel(title = s.panelQuickSend) {
            QuickSendPanel(vm)
        }

        ExpandableCardPanel(title = s.panelSendHistory) {
            SendHistoryPanel(vm)
        }

        ExpandableCardPanel(title = s.panelProtocolTools) {
            ProtocolToolsPanel(vm)
        }
    }
}

@Composable
fun QuickSendPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val prefs = vm.prefs
    val commands = remember(prefs.quickCommandsText) {
        prefs.quickCommandsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(8)
            .toList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (commands.isEmpty()) {
            Text(strings.quickSendEmpty, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.52f))
        } else {
            commands.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { command ->
                        // 保留输入框中的可见转义，由 ViewModel 与普通发送共用同一套完整解析规则。
                        QuickCommandButton(
                            text = command,
                            modifier = Modifier.weight(1f),
                            enabled = vm.canSend(command)
                        ) { vm.sendData(command) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.TopStart
        ) {
            BasicTextField(
                value = prefs.quickCommandsText,
                onValueChange = { prefs.quickCommandsText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary)
            )
        }
        Text(strings.quickSendHint, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.48f))
    }
}

@Composable
private fun PortParameterRow(vm: SerialViewModel) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val details = vm.availablePortDetails
    val labels = if (details.isNotEmpty()) details.map { it.displayName } else vm.availablePorts
    // 地址框既接受系统串口名，也接受 TransportRouter 认识的网络地址，
    // 所以这里不做格式限制，只在下拉里提供检测到的串口作为快捷选择。
    val isNetwork = vm.config.selectedPort.contains("://")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ParameterInputRow(
            label = strings.labelAddress,
            value = vm.config.selectedPort,
            labelWidth = 84.dp,
            minHeight = 34.dp,
            placeholder = strings.addressPlaceholder,
            // 连接中禁止编辑：地址逐字符变化会触发自动重连，想换端点先断开。
            enabled = !vm.isConnectionActive,
            onValueChange = { vm.config.selectedPort = it.trim() }
        )
        ParameterRow(
            label = strings.labelDetectedPorts,
            value = details.firstOrNull { it.systemName == vm.config.selectedPort }?.displayName
                ?: strings.notSelected,
            options = labels,
            menuWidth = 292.dp,
            optionMaxLines = 2,
            labelWidth = 84.dp,
            fieldHeight = 32.dp
        ) { selected ->
            vm.config.selectedPort = details.firstOrNull { it.displayName == selected }?.systemName ?: selected
        }
        if (isNetwork) {
            Text(
                strings.addressNetworkHint,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = colors.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 动作按钮网格（2×2 排列）
 */
@Composable
private fun ActionButtonGrid(vm: SerialViewModel, onClearSend: () -> Unit) {
    val colors = LocalAppColors.current
    val s = LocalAppStrings.current
    val hasLogs by remember(vm) { derivedStateOf { vm.logs.isNotEmpty() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = if (vm.isConnectionActive) s.btnClosePort else s.btnOpenPort,
                color = colors.primary,
                modifier = Modifier.weight(1f),
                enabled = vm.isConnectionActive || vm.config.selectedPort.isNotBlank()
            ) { vm.togglePort() }
            ActionButton(s.btnClearReceive, colors.surface, Modifier.weight(1f), enabled = hasLogs) { vm.clearLogs() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                s.btnSendData,
                colors.success,
                Modifier.weight(1f),
                enabled = vm.canSend(vm.inputText)
            ) { vm.sendCurrentInput() }
            ActionButton(
                s.btnClearSend,
                colors.surface,
                Modifier.weight(1f),
                enabled = vm.inputText.isNotEmpty()
            ) { onClearSend() }
        }
    }
}

/**
 * 选项复选框网格
 */
@Composable
fun OptionsGrid(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val prefs = vm.prefs
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            OptionItem(prefs.showRealTime, s.optRealTime, Modifier.weight(1f), hint = s.hintRealTime) { prefs.showRealTime = it }
            OptionItem(prefs.hexDisplay, s.optHexDisplay, Modifier.weight(1f), hint = s.hintHexDisplay) { prefs.hexDisplay = it }
        }
        Row {
            OptionItem(prefs.hexSend, s.optHexSend, Modifier.weight(1f), hint = s.hintHexSend) { prefs.hexSend = it }
            OptionItem(
                prefs.loopSendEnabled,
                s.optLoopSend,
                Modifier.weight(1f),
                enabled = prefs.isLoopIntervalValid,
                hint = s.hintLoopSend
            ) { prefs.loopSendEnabled = it }
        }
        Row {
            OptionItem(prefs.saveSend, s.optSaveSend, Modifier.weight(1f), hint = s.hintSaveSend) { prefs.saveSend = it }
            OptionItem(prefs.saveReceive, s.optSaveReceive, Modifier.weight(1f), hint = s.hintSaveReceive) { prefs.saveReceive = it }
        }
        Row {
            OptionItem(prefs.enterToSend, s.optEnterToSend, Modifier.weight(1f), hint = s.hintEnterToSend) { prefs.enterToSend = it }
            OptionItem(prefs.clearAfterSend, s.optClearAfterSend, Modifier.weight(1f), hint = s.hintClearAfterSend) { prefs.clearAfterSend = it }
        }
        OptionItem(prefs.autoReconnect, s.optAutoReconnect, Modifier.fillMaxWidth(), hint = s.hintAutoReconnect) { vm.setAutoReconnect(it) }
        ParameterRow(
            label = s.labelLineEnding,
            value = lineEndingLabel(prefs.sendLineEnding, s),
            options = SendLineEnding.entries.map { lineEndingLabel(it, s) }
        ) { selected ->
            prefs.sendLineEnding = SendLineEnding.entries.first { lineEndingLabel(it, s) == selected }
        }
        if (prefs.autoReconnect) {
            CompactNumericRow(
                label = s.labelReconnectInterval,
                value = prefs.reconnectIntervalText,
                onValueChange = { prefs.reconnectIntervalText = it },
                suffix = s.labelMs
            )
        }
        // 发送周期输入行
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            val colors = LocalAppColors.current
            Text(s.labelInterval, fontSize = 12.sp, color = colors.onSurface)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(65.dp)
                    .height(24.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        if (prefs.isLoopIntervalValid) colors.border else colors.error,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = prefs.loopIntervalText,
                    onValueChange = { if (it.all(Char::isDigit)) prefs.loopIntervalText = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(s.labelMs, fontSize = 12.sp, color = colors.onSurface)
        }
        if (!prefs.isLoopIntervalValid) {
            Text(s.errorLoopInterval, fontSize = 11.sp, color = LocalAppColors.current.error)
        }
    }
}

@Composable
private fun ReceiveDisplayPanel(vm: SerialViewModel) {
    val strings = LocalAppStrings.current
    val prefs = vm.prefs
    ParameterRow(
        label = strings.labelReceiveMode,
        value = receiveModeLabel(prefs.receiveDisplayMode, strings),
        options = ReceiveDisplayMode.entries.map { receiveModeLabel(it, strings) }
    ) { selected ->
        prefs.receiveDisplayMode = ReceiveDisplayMode.entries.first { receiveModeLabel(it, strings) == selected }
    }
    when (prefs.receiveDisplayMode) {
        ReceiveDisplayMode.TIME_WINDOW -> CompactNumericRow(
            strings.labelReceiveWindow,
            prefs.receiveWindowMsText,
            { prefs.receiveWindowMsText = it },
            strings.labelMs
        )
        ReceiveDisplayMode.DELIMITER -> CompactTextRow(
            strings.labelReceiveDelimiter,
            prefs.receiveDelimiterText,
            { prefs.receiveDelimiterText = it }
        )
        ReceiveDisplayMode.FIXED_LENGTH -> CompactNumericRow(
            strings.labelReceiveFrameSize,
            prefs.receiveFrameSizeText,
            { prefs.receiveFrameSizeText = it },
            "B"
        )
        ReceiveDisplayMode.RAW_CHUNKS -> Unit
    }
}

@Composable
private fun LineControlPanel(vm: SerialViewModel) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ActionButton(
            strings.labelDtr,
            if (vm.dtrEnabled) colors.success else colors.surface,
            Modifier.weight(1f),
            enabled = vm.isPortOpen && vm.config.flowControlIndex != 2,
            hint = strings.hintDtr
        ) { vm.setDtr(!vm.dtrEnabled) }
        ActionButton(
            strings.labelRts,
            if (vm.rtsEnabled) colors.success else colors.surface,
            Modifier.weight(1f),
            enabled = vm.isPortOpen && vm.config.flowControlIndex != 1,
            hint = strings.hintRts
        ) { vm.setRts(!vm.rtsEnabled) }
        ActionButton(
            strings.labelBreak,
            if (vm.breakEnabled) colors.error else colors.surface,
            Modifier.weight(1f),
            enabled = vm.isPortOpen,
            hint = strings.hintBreak
        ) { vm.setBreak(!vm.breakEnabled) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SignalIndicator("CTS", vm.lineSignals.cts, Modifier.weight(1f), hint = strings.hintCts)
        SignalIndicator("DSR", vm.lineSignals.dsr, Modifier.weight(1f), hint = strings.hintDsr)
        SignalIndicator("DCD", vm.lineSignals.dcd, Modifier.weight(1f), hint = strings.hintDcd)
        SignalIndicator("RI", vm.lineSignals.ri, Modifier.weight(1f), hint = strings.hintRi)
    }
}

@Composable
private fun CompactNumericRow(label: String, value: String, onValueChange: (String) -> Unit, suffix: String) {
    ParameterInputRow(label, value, onValueChange, suffix = suffix, numericOnly = true)
}

@Composable
private fun CompactTextRow(label: String, value: String, onValueChange: (String) -> Unit, suffix: String = "") {
    ParameterInputRow(label, value, onValueChange, suffix = suffix)
}

private fun receiveModeLabel(mode: ReceiveDisplayMode, strings: com.yiaobang.kserial.i18n.AppStrings): String = when (mode) {
    ReceiveDisplayMode.TIME_WINDOW -> strings.receiveModeTimeWindow
    ReceiveDisplayMode.RAW_CHUNKS -> strings.receiveModeRawChunks
    ReceiveDisplayMode.DELIMITER -> strings.receiveModeDelimiter
    ReceiveDisplayMode.FIXED_LENGTH -> strings.receiveModeFixedLength
}

internal fun lineEndingLabel(ending: SendLineEnding, strings: com.yiaobang.kserial.i18n.AppStrings): String = when (ending) {
    SendLineEnding.NONE -> strings.lineEndingNone
    SendLineEnding.LF -> "LF (\\n)"
    SendLineEnding.CR -> "CR (\\r)"
    SendLineEnding.CRLF -> "CRLF (\\r\\n)"
}

@Composable
private fun ConnectionFeedback(vm: SerialViewModel) {
    if (vm.connectionState == SerialConnectionState.CONNECTED ||
        vm.connectionState == SerialConnectionState.DISCONNECTED
    ) return

    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val stateColor = when (vm.connectionState) {
        SerialConnectionState.ERROR -> colors.error
        SerialConnectionState.CONNECTING, SerialConnectionState.RECONNECTING -> colors.primary
        SerialConnectionState.CONNECTED -> colors.success
        SerialConnectionState.DISCONNECTED -> colors.onSurface.copy(alpha = 0.30f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                stateColor.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (vm.connectionState) {
                    SerialConnectionState.CONNECTED -> strings.statusConnected
                    SerialConnectionState.CONNECTING -> strings.statusConnecting
                    SerialConnectionState.RECONNECTING -> strings.statusReconnecting
                    SerialConnectionState.ERROR -> strings.statusError
                    SerialConnectionState.DISCONNECTED -> strings.statusDisconnected
                },
                color = colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (vm.connectionDetail.isNotBlank()) {
                Text(
                    text = vm.connectionDetail,
                    color = if (vm.connectionState == SerialConnectionState.ERROR) colors.error else colors.onSurface.copy(alpha = 0.52f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

