package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.ConfigIssue
import com.yiaobang.kserial.model.ConfigIssueSeverity
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.utils.ChecksumAlgorithm
import com.yiaobang.kserial.utils.FrameParser
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.toHexString

@Composable
fun SendHistoryPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val history = vm.sendHistory.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (history.isEmpty()) {
            Text(strings.historyEmpty, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.52f))
        } else {
            history.take(8).forEach { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 38.dp)
                        .background(colors.background.copy(alpha = 0.76f), RoundedCornerShape(5.dp))
                        .border(1.dp, colors.border.copy(alpha = 0.48f), RoundedCornerShape(5.dp))
                        .clickable { vm.selectHistory(command) }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = command.visibleCommand(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        color = colors.onSurface.copy(alpha = 0.82f)
                    )
                    IconButton(
                        onClick = { vm.promoteHistoryToQuickSend(command) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, strings.actionAddQuick, Modifier.size(17.dp), tint = colors.primary)
                    }
                    IconButton(
                        onClick = { vm.sendHistory.remove(command) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, strings.actionRemoveHistory, Modifier.size(17.dp), tint = colors.onSurface.copy(alpha = 0.48f))
                    }
                }
            }
            ActionButton(
                text = strings.btnClearHistory,
                color = colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) { vm.sendHistory.clear() }
        }
        Text(strings.historyKeyboardHint, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.46f))
    }
}

@Composable
fun ProtocolToolsPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    var algorithm by remember { mutableStateOf(ChecksumAlgorithm.CRC16_MODBUS) }
    var frameMode by remember { mutableStateOf(FrameMode.FIXED) }
    var frameSize by remember { mutableStateOf("8") }
    var delimiterHex by remember { mutableStateOf("0D 0A") }

    val checksum = remember(vm.inputText, vm.prefs.hexSend, algorithm) { vm.checksum(algorithm) }
    val frameResult = remember(vm.inputText, vm.prefs.hexSend, frameMode, frameSize, delimiterHex) {
        val input = vm.inputBytesOrNull() ?: return@remember null
        runCatching {
            when (frameMode) {
                FrameMode.FIXED -> FrameParser.fixedLength(input, frameSize.toInt())
                FrameMode.DELIMITER -> {
                    val delimiter = HexInputParser.parse(delimiterHex).bytes ?: error("invalid delimiter")
                    FrameParser.delimiter(input, delimiter)
                }
            }
        }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolSelectRow(
            label = strings.labelChecksum,
            value = algorithm.label,
            options = ChecksumAlgorithm.entries.map { it.label }
        ) { selected -> algorithm = ChecksumAlgorithm.entries.first { it.label == selected } }
        Text(
            text = checksum?.let { strings.checksumResult(it.readableHex()) } ?: strings.checksumUnavailable,
            fontSize = 11.sp,
            color = if (checksum == null) colors.onSurface.copy(alpha = 0.48f) else colors.primary
        )
        ActionButton(
            text = strings.btnAppendChecksum,
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
            enabled = checksum != null
        ) { vm.appendChecksum(algorithm) }

        Spacer(Modifier.height(2.dp))
        ToolSelectRow(
            label = strings.labelFrameMode,
            value = if (frameMode == FrameMode.FIXED) strings.frameModeFixed else strings.frameModeDelimiter,
            options = listOf(strings.frameModeFixed, strings.frameModeDelimiter)
        ) { selected -> frameMode = if (selected == strings.frameModeFixed) FrameMode.FIXED else FrameMode.DELIMITER }
        if (frameMode == FrameMode.FIXED) {
            ToolInputRow(strings.labelFrameSize, frameSize) { value ->
                if (value.all(Char::isDigit)) frameSize = value
            }
        } else {
            ToolInputRow(strings.labelDelimiter, delimiterHex) { delimiterHex = it }
        }
        Text(
            text = frameResult?.let { strings.frameResult(it.frames.size, it.remainder.size) } ?: strings.frameUnavailable,
            fontSize = 11.sp,
            color = if (frameResult == null) colors.onSurface.copy(alpha = 0.48f) else colors.onSurface.copy(alpha = 0.72f)
        )
        frameResult?.frames?.take(4)?.forEachIndexed { index, frame ->
            Text(
                text = "${index + 1}. ${frame.readableHex()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = colors.onSurface.copy(alpha = 0.62f)
            )
        }
    }
}

private enum class FrameMode { FIXED, DELIMITER }

private val ChecksumAlgorithm.label: String
    get() = when (this) {
        ChecksumAlgorithm.CRC16_MODBUS -> "CRC16-Modbus"
        ChecksumAlgorithm.XOR -> "XOR"
        ChecksumAlgorithm.SUM8 -> "SUM8"
        ChecksumAlgorithm.LRC -> "LRC"
    }

@Composable
private fun ToolSelectRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(94.dp), fontSize = 12.sp, color = colors.onSurface)
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                    .clickable { expanded = true }
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, modifier = Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurface)
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(17.dp), tint = colors.primary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 12.sp) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolInputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(94.dp), fontSize = 12.sp, color = colors.onSurface)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary)
            )
        }
    }
}

private fun String.visibleCommand(): String = replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
private fun ByteArray.readableHex(): String = toHexString().chunked(2).joinToString(" ")

@Composable
fun ConfigValidationSummary(issues: List<ConfigIssue>) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    if (issues.isEmpty()) {
        Text(strings.configValid, fontSize = 11.sp, color = colors.success)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        issues.take(5).forEach { issue ->
            val prefix = if (issue.severity == ConfigIssueSeverity.ERROR) "●" else "▲"
            Text(
                text = "$prefix ${issue.message}",
                fontSize = 11.sp,
                color = if (issue.severity == ConfigIssueSeverity.ERROR) colors.error else colors.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}
