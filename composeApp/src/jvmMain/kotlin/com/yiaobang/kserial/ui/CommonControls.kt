package com.yiaobang.kserial.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.theme.LocalAppColors

// 跨工作区复用的表单控件。组件只从 LocalAppColors 取色，不依赖任何 ViewModel。

/** 通用卡片面板：小节标题 + 纵向内容，底部带分割线。 */
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

/** 可折叠卡片面板：标题行带展开指示图标。 */
@Composable
fun ExpandableCardPanel(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val colors = LocalAppColors.current
    val strings = com.yiaobang.kserial.i18n.LocalAppStrings.current
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

/** 卡片内部的小节折叠开关，样式比 ExpandableCardPanel 更紧凑。 */
@Composable
fun ExpandableSectionToggle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = com.yiaobang.kserial.i18n.LocalAppStrings.current
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

/** 复选框选项，整行可点击切换；提供 hint 时悬停显示说明。 */
@Composable
fun OptionItem(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    onChecked: (Boolean) -> Unit
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) { onChecked(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, enabled = enabled, onCheckedChange = onChecked)
            Text(
                label,
                fontSize = 12.sp,
                color = LocalAppColors.current.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            )
        }
    }
    if (hint == null) row() else AppTooltip(hint) { row() }
}

/**
 * 通用操作按钮。主色（primary/success/error）填充白字，其余颜色视为次级按钮并带边框；
 * 提供 hint 时悬停显示说明（适合 DTR/RTS 等缩写按钮）。
 */
@Composable
fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val isMainColor = color == colors.primary || color == colors.success || color == colors.error
    val button: @Composable () -> Unit = {
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
    if (hint == null) button() else AppTooltip(hint) { button() }
}

/** 快捷命令小按钮：左侧带发送图标。 */
@Composable
fun QuickCommandButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val contentAlpha = if (enabled) 1f else 0.34f
    // 命令超长被省略号截断时，悬停可看完整内容。
    AppTooltip(text) {
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
}

/** 硬件线路信号指示：圆点灯 + 信号名；提供 hint 时悬停解释该信号线的含义。 */
@Composable
fun SignalIndicator(label: String, active: Boolean, modifier: Modifier = Modifier, hint: String? = null) {
    val colors = LocalAppColors.current
    val row: @Composable () -> Unit = {
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
    if (hint == null) row() else AppTooltip(hint) { row() }
}

/**
 * 参数下拉选择行：左侧固定宽度标签，右侧下拉选择框。
 * 默认样式与控制面板一致；协议工具等紧凑场景可通过参数覆写外观。
 */
@Composable
fun ParameterRow(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    menuWidth: Dp? = 176.dp,
    optionMaxLines: Int = 1,
    labelWidth: Dp = 108.dp,
    fieldHeight: Dp = 36.dp,
    fieldShape: RoundedCornerShape = RoundedCornerShape(7.dp),
    fieldBackground: Color? = null,
    fieldBorder: Color? = null,
    arrowTint: Color? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(labelWidth), fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.78f))
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .clip(fieldShape)
                    .background(fieldBackground ?: colors.surfaceMuted)
                    .border(1.dp, fieldBorder ?: colors.border.copy(alpha = 0.82f), fieldShape)
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
                Icon(
                    Icons.Default.ArrowDropDown,
                    null,
                    modifier = Modifier.align(Alignment.CenterEnd).size(17.dp),
                    tint = arrowTint ?: colors.onSurface.copy(alpha = 0.54f)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .then(if (menuWidth != null) Modifier.width(menuWidth) else Modifier)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.72f))
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
 * 参数文本输入行：左侧固定宽度标签，右侧单行输入框。
 * numericOnly = true 时只允许输入数字。
 */
@Composable
fun ParameterInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    labelWidth: Dp = 110.dp,
    minHeight: Dp = 36.dp,
    numericOnly: Boolean = false,
    placeholder: String = "",
    enabled: Boolean = true
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            fontSize = 12.sp,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.45f)
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { next ->
                if (enabled && (!numericOnly || next.all(Char::isDigit))) onValueChange(next)
            },
            singleLine = true,
            readOnly = !enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minHeight)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.55f)
            ),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = 0.34f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        )
        if (suffix.isNotEmpty()) Text(suffix, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
    }
}
