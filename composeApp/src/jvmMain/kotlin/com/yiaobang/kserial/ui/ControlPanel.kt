package com.yiaobang.kserial.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogFilterConfig
import com.yiaobang.kserial.model.ReceiveDisplayMode
import com.yiaobang.kserial.model.SendLineEnding
import com.yiaobang.kserial.model.SerialConnectionState
import com.yiaobang.kserial.theme.LocalAppColors

/**
 * 右侧控制面板容器，包含连接参数、动作按钮、选项复选框三张卡片
 */
@Composable
fun ControlPanel(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val config = vm.config
    val prefs = vm.prefs
    var advancedConnectionExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 连接参数配置卡片
        CardPanel(title = s.panelConnection) {
            ConnectionFeedback(vm)
            PortParameterRow(vm)
            ParameterRow(s.labelBaudRate, config.baudRate.toString(), com.yiaobang.kserial.model.SerialPortConfig.BAUD_RATES.map { it.toString() }) { config.baudRate = it.toInt() }
            ExpandableSectionToggle(
                title = s.labelAdvancedSerialParameters,
                expanded = advancedConnectionExpanded,
                onToggle = { advancedConnectionExpanded = !advancedConnectionExpanded }
            )
            if (advancedConnectionExpanded) {
                ParameterRow(s.labelDataBits, config.dataBits.toString(), com.yiaobang.kserial.model.SerialPortConfig.DATA_BITS_LIST.map { it.toString() }) { config.dataBits = it.toInt() }
                ParameterRow(s.labelStopBits, s.stopBitsOptions[config.stopBitsIndex], s.stopBitsOptions) { config.stopBitsIndex = s.stopBitsOptions.indexOf(it) }
                ParameterRow(s.labelParity, s.parityOptions[config.parityIndex], s.parityOptions) { config.parityIndex = s.parityOptions.indexOf(it) }
                ParameterRow(s.labelFlowControl, s.flowControlOptions[config.flowControlIndex], s.flowControlOptions) { config.flowControlIndex = s.flowControlOptions.indexOf(it) }
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

        ExpandableCardPanel(title = s.panelHighlight) {
            HighlightEditor(prefs.highlight)
        }

        ExpandableCardPanel(title = s.panelLog) {
            LogFilterEditor(prefs.logFilter)
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

/**
 * 通用卡片面板布局
 */
@Composable
fun CardPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface.copy(0.58f),
                letterSpacing = 0.35.sp
            )
            content()
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))
    }
}

@Composable
private fun PortParameterRow(vm: SerialViewModel) {
    val strings = LocalAppStrings.current
    val details = vm.availablePortDetails
    val selectedLabel = details.firstOrNull { it.systemName == vm.config.selectedPort }?.displayName
        ?: vm.config.selectedPort.ifBlank { strings.notSelected }
    val labels = if (details.isNotEmpty()) details.map { it.displayName } else vm.availablePorts
    ParameterRow(
        label = strings.labelSerialPort,
        value = selectedLabel,
        options = labels,
        menuWidth = 292.dp,
        optionMaxLines = 2
    ) { selected ->
        vm.config.selectedPort = details.firstOrNull { it.displayName == selected }?.systemName ?: selected
    }
}

@Composable
fun ExpandableCardPanel(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface.copy(alpha = 0.76f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) strings.actionCollapse else strings.actionExpand,
                modifier = Modifier.size(18.dp),
                tint = if (expanded) colors.primary else colors.onSurface.copy(alpha = 0.46f)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))
    }
}

@Composable
private fun ExpandableSectionToggle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.background.copy(alpha = 0.72f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface.copy(alpha = 0.74f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) strings.actionCollapse else strings.actionExpand,
            modifier = Modifier.size(18.dp),
            tint = colors.primary.copy(alpha = 0.76f)
        )
    }
}

/**
 * 参数下拉选择行
 */
@Composable
private fun ParameterRow(
    label: String,
    value: String,
    options: List<String>,
    menuWidth: Dp = 176.dp,
    optionMaxLines: Int = 1,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(108.dp), fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.78f))
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.border.copy(alpha = 0.82f), RoundedCornerShape(7.dp))
                    .clickable(enabled = options.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(end = 20.dp),
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.align(Alignment.CenterEnd).size(17.dp), tint = colors.onSurface.copy(alpha = 0.54f))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(menuWidth).background(colors.surface).border(1.dp, colors.border.copy(alpha = 0.72f))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = colors.onSurface,
                                maxLines = optionMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

/**
 * 动作按钮网格（3×2 排列）
 */
@Composable
private fun ActionButtonGrid(vm: SerialViewModel, onClearSend: () -> Unit) {
    val colors = LocalAppColors.current
    val s = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = if (vm.isConnectionActive) s.btnClosePort else s.btnOpenPort,
                color = colors.primary,
                modifier = Modifier.weight(1f),
                enabled = vm.isConnectionActive || vm.config.selectedPort.isNotBlank()
            ) { vm.togglePort() }
            ActionButton(s.btnClearReceive, colors.surface, Modifier.weight(1f), enabled = vm.logs.isNotEmpty()) { vm.clearLogs() }
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
 * 选项复选框网格（3×2 排列）
 */
@Composable
fun OptionsGrid(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val prefs = vm.prefs
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            OptionItem(prefs.showRealTime, s.optRealTime, Modifier.weight(1f)) { prefs.showRealTime = it }
            OptionItem(prefs.hexDisplay, s.optHexDisplay, Modifier.weight(1f)) { prefs.hexDisplay = it }
        }
        Row {
            OptionItem(prefs.hexSend, s.optHexSend, Modifier.weight(1f)) { prefs.hexSend = it }
            OptionItem(
                prefs.loopSendEnabled,
                s.optLoopSend,
                Modifier.weight(1f),
                enabled = prefs.isLoopIntervalValid
            ) { prefs.loopSendEnabled = it }
        }
        Row {
            OptionItem(prefs.saveSend, s.optSaveSend, Modifier.weight(1f)) { prefs.saveSend = it }
            OptionItem(prefs.saveReceive, s.optSaveReceive, Modifier.weight(1f)) { prefs.saveReceive = it }
        }
        Row {
            OptionItem(prefs.enterToSend, s.optEnterToSend, Modifier.weight(1f)) { prefs.enterToSend = it }
            OptionItem(prefs.clearAfterSend, s.optClearAfterSend, Modifier.weight(1f)) { prefs.clearAfterSend = it }
        }
        OptionItem(prefs.autoReconnect, s.optAutoReconnect, Modifier.fillMaxWidth()) { vm.setAutoReconnect(it) }
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
            enabled = vm.isPortOpen && vm.config.flowControlIndex != 2
        ) { vm.setDtr(!vm.dtrEnabled) }
        ActionButton(
            strings.labelRts,
            if (vm.rtsEnabled) colors.success else colors.surface,
            Modifier.weight(1f),
            enabled = vm.isPortOpen && vm.config.flowControlIndex != 1
        ) { vm.setRts(!vm.rtsEnabled) }
        ActionButton(
            strings.labelBreak,
            if (vm.breakEnabled) colors.error else colors.surface,
            Modifier.weight(1f),
            enabled = vm.isPortOpen
        ) { vm.setBreak(!vm.breakEnabled) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SignalIndicator("CTS", vm.lineSignals.cts, Modifier.weight(1f))
        SignalIndicator("DSR", vm.lineSignals.dsr, Modifier.weight(1f))
        SignalIndicator("DCD", vm.lineSignals.dcd, Modifier.weight(1f))
        SignalIndicator("RI", vm.lineSignals.ri, Modifier.weight(1f))
    }
}

@Composable
private fun SignalIndicator(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(colors.background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) colors.success else colors.onSurface.copy(alpha = 0.28f))
        )
        Text(label, fontSize = 10.sp, color = colors.onSurface.copy(alpha = 0.72f))
    }
}

@Composable
private fun CompactNumericRow(label: String, value: String, onValueChange: (String) -> Unit, suffix: String) {
    CompactTextRow(label, value, { next -> if (next.all(Char::isDigit)) onValueChange(next) }, suffix)
}

@Composable
private fun CompactTextRow(label: String, value: String, onValueChange: (String) -> Unit, suffix: String = "") {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(110.dp), fontSize = 12.sp, color = colors.onSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary)
        )
        if (suffix.isNotEmpty()) Text(suffix, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
    }
}

private fun receiveModeLabel(mode: ReceiveDisplayMode, strings: com.yiaobang.kserial.i18n.AppStrings): String = when (mode) {
    ReceiveDisplayMode.TIME_WINDOW -> strings.receiveModeTimeWindow
    ReceiveDisplayMode.RAW_CHUNKS -> strings.receiveModeRawChunks
    ReceiveDisplayMode.DELIMITER -> strings.receiveModeDelimiter
    ReceiveDisplayMode.FIXED_LENGTH -> strings.receiveModeFixedLength
}

private fun lineEndingLabel(ending: SendLineEnding, strings: com.yiaobang.kserial.i18n.AppStrings): String = when (ending) {
    SendLineEnding.NONE -> strings.lineEndingNone
    SendLineEnding.LF -> "LF (\\n)"
    SendLineEnding.CR -> "CR (\\r)"
    SendLineEnding.CRLF -> "CRLF (\\r\\n)"
}

/**
 * 通用操作按钮
 */
@Composable
fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val isMainColor = color == colors.primary || color == colors.success
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.onSurface.copy(alpha = 0.34f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        border = if (!isMainColor) BorderStroke(1.dp, colors.border) else null
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (!enabled) colors.onSurface.copy(alpha = 0.34f)
            else if (isMainColor) Color.White else colors.onSurface
        )
    }
}

@Composable
private fun QuickCommandButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val contentAlpha = if (enabled) 1f else 0.34f
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceMuted),
        shape = RoundedCornerShape(5.dp),
        contentPadding = PaddingValues(horizontal = 7.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.72f))
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = colors.primary.copy(alpha = 0.82f * contentAlpha)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.onSurface.copy(alpha = 0.84f * contentAlpha)
        )
    }
}

@Composable
fun HighlightEditor(highlight: KeywordHighlightConfig) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            OptionItem(highlight.enabled, strings.optEnable, Modifier.weight(1f)) { highlight.enabled = it }
            OptionItem(highlight.ignoreCase, strings.optIgnoreCase, Modifier.weight(1f)) { highlight.ignoreCase = it }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(colors.surfaceMuted, RoundedCornerShape(5.dp))
                .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.TopStart
        ) {
            BasicTextField(
                value = highlight.keywordsText,
                onValueChange = { highlight.keywordsText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary)
            )
            if (highlight.keywordsText.isBlank()) {
                Text(
                    text = "ERROR, WARN, OK",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.32f)
                )
            }
        }
        Text(
            text = strings.keywordSeparatorHint,
            fontSize = 11.sp,
            color = colors.onSurface.copy(alpha = 0.48f)
        )
    }
}

@Composable
fun LogFilterEditor(filter: LogFilterConfig) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                .background(colors.surfaceMuted, RoundedCornerShape(5.dp))
                .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = filter.query,
                onValueChange = { filter.query = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary)
            )
            if (filter.query.isBlank()) {
                Text(strings.searchLog, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.32f))
            }
        }
        Row {
            OptionItem(filter.onlyMatches, strings.optOnlyMatches, Modifier.weight(1f)) { filter.onlyMatches = it }
            OptionItem(filter.autoScroll, strings.optAutoScroll, Modifier.weight(1f)) { filter.autoScroll = it }
        }
        Row {
            OptionItem(filter.showRx, "RX", Modifier.weight(1f)) { filter.showRx = it }
            OptionItem(filter.showTx, "TX", Modifier.weight(1f)) { filter.showTx = it }
            OptionItem(filter.showInfo, "INFO", Modifier.weight(1f)) { filter.showInfo = it }
        }
        ActionButton(strings.btnResetLogFilter, colors.surface, Modifier.fillMaxWidth()) {
            filter.reset()
        }
    }
}

/**
 * 复选框选项行
 */
@Composable
private fun OptionItem(
    checked: Boolean,
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onChecked)
        Text(
            label,
            fontSize = 12.sp,
            color = LocalAppColors.current.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        )
    }
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
