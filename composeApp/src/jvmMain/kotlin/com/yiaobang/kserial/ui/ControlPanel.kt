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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.theme.LocalAppColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * 右侧控制面板容器，包含连接参数、动作按钮、选项复选框三张卡片
 */
@Composable
fun ControlPanel(vm: SerialViewModel, onNewWindow: () -> Unit) {
    val s = vm.strings
    val config = vm.config
    val prefs = vm.prefs
    Column(
        modifier = Modifier.width(310.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 连接参数配置卡片
        CardPanel(title = "CONNECTION") {
            ParameterRow(s.labelSerialPort, config.selectedPort, vm.availablePorts) { config.selectedPort = it }
            ParameterRow(s.labelBaudRate, config.baudRate.toString(), com.yiaobang.kserial.model.SerialPortConfig.BAUD_RATES.map { it.toString() }) { config.baudRate = it.toInt() }
            ParameterRow(s.labelDataBits, config.dataBits.toString(), com.yiaobang.kserial.model.SerialPortConfig.DATA_BITS_LIST.map { it.toString() }) { config.dataBits = it.toInt() }
            ParameterRow(s.labelStopBits, s.stopBitsOptions[config.stopBitsIndex], s.stopBitsOptions) { config.stopBitsIndex = s.stopBitsOptions.indexOf(it) }
            ParameterRow(s.labelParity, s.parityOptions[config.parityIndex], s.parityOptions) { config.parityIndex = s.parityOptions.indexOf(it) }
            ParameterRow(s.labelFlowControl, s.flowControlOptions[config.flowControlIndex], s.flowControlOptions) { config.flowControlIndex = s.flowControlOptions.indexOf(it) }
        }

        // 动作按钮卡片
        CardPanel(title = "ACTIONS") {
            ActionButtonGrid(vm = vm, onNewWindow = onNewWindow, onClearSend = { vm.inputText = "" })
        }

        // 选项复选框卡片
        CardPanel(title = "OPTIONS") {
            OptionsGrid(vm = vm)
        }
    }
}

/**
 * 通用卡片面板布局
 */
@Composable
fun CardPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(0.4f))
        content()
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
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(100.dp), fontSize = 13.sp, color = colors.onSurface)
        Box(modifier = Modifier.width(160.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(value, fontSize = 13.sp, color = colors.onSurface)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.align(Alignment.CenterEnd).size(16.dp), tint = colors.primary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(160.dp).background(colors.surface).border(1.dp, colors.border)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp, color = colors.onSurface) },
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
private fun ActionButtonGrid(vm: SerialViewModel, onNewWindow: () -> Unit, onClearSend: () -> Unit) {
    val colors = LocalAppColors.current
    val s = vm.strings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = if (vm.isPortOpen) s.btnClosePort else s.btnOpenPort,
                color = colors.primary,
                modifier = Modifier.weight(1f)
            ) { vm.togglePort() }
            ActionButton(s.btnClearReceive, colors.surface, Modifier.weight(1f)) { vm.clearLogs() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(s.btnSendData, colors.success, Modifier.weight(1f)) { vm.sendData(vm.inputText) }
            ActionButton(s.btnClearSend, colors.surface, Modifier.weight(1f)) { onClearSend() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(s.btnNewWindow, colors.surface, Modifier.weight(1f), onNewWindow)
            ActionButton(
                text = if (vm.isSimulating) "正在模拟" else s.btnAnalogReply,
                color = if (vm.isSimulating) colors.success else colors.surface,
                modifier = Modifier.weight(1f)
            ) {
                val dialog = FileDialog(null as Frame?, "选择 JSON 模拟配置文件", FileDialog.LOAD).apply {
                    file = "*.json"
                    isVisible = true
                }
                dialog.file?.let { vm.loadSimulator(File(dialog.directory, it)) }
            }
        }
        // 状态指示灯
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.padding(top = 4.dp)) {
            StatusDot(vm.isPortOpen, s.labelStatusPort)
            StatusDot(vm.isSimulating, s.labelStatusSim)
        }
    }
}

/**
 * 选项复选框网格（3×2 排列）
 */
@Composable
private fun OptionsGrid(vm: SerialViewModel) {
    val s = vm.strings
    val prefs = vm.prefs
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            OptionItem(prefs.showRealTime, s.optRealTime, Modifier.weight(1f)) { prefs.showRealTime = it }
            OptionItem(prefs.hexDisplay, s.optHexDisplay, Modifier.weight(1f)) { prefs.hexDisplay = it }
        }
        Row {
            OptionItem(prefs.hexSend, s.optHexSend, Modifier.weight(1f)) { prefs.hexSend = it }
            OptionItem(prefs.loopSendEnabled, s.optLoopSend, Modifier.weight(1f)) { prefs.loopSendEnabled = it }
        }
        Row {
            OptionItem(prefs.saveSend, s.optSaveSend, Modifier.weight(1f)) { prefs.saveSend = it }
            OptionItem(prefs.saveReceive, s.optSaveReceive, Modifier.weight(1f)) { prefs.saveReceive = it }
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
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
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
    }
}

/**
 * 通用操作按钮
 */
@Composable
fun ActionButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val isMainColor = color == colors.primary || color == colors.success
    Button(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        border = if (!isMainColor) BorderStroke(1.dp, colors.border) else null
    ) {
        Text(text, fontSize = 12.sp, color = if (isMainColor) Color.White else colors.onSurface)
    }
}

/**
 * 状态指示圆点
 */
@Composable
private fun StatusDot(active: Boolean, label: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF34C759) else Color(0xFFFF3B30))
        )
        Text(label, fontSize = 11.sp, color = colors.onSurface.copy(0.7f))
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
    onChecked: (Boolean) -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, fontSize = 12.sp, color = LocalAppColors.current.onSurface)
    }
}
