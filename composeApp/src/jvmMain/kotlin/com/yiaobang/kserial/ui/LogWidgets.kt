package com.yiaobang.kserial.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogFilterConfig
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.utils.DesktopActions

// 日志区共享组件：串口终端、设备模拟和桥接页面的日志面板都从这里取用。

/**
 * 日志列表顶部标题行：条目计数、搜索框、日志选项弹层，以及复制/导出/清空操作。
 *
 * 搜索、高亮和筛选都属于"日志怎么显示"，因此统一收在日志头部，不再占用右侧控制面板。
 * `onQueryChange` 为 null 时退化为只读搜索词提示（例如桥接页暂未开放行内搜索）。
 */
@Composable
fun LogListHeader(
    totalCount: Int,
    visibleCount: Int,
    query: String,
    onCopyLogs: () -> Unit,
    onExportText: () -> Unit,
    onExportCsv: () -> Unit,
    onClearLogs: () -> Unit,
    onQueryChange: ((String) -> Unit)? = null,
    highlight: KeywordHighlightConfig? = null,
    filter: LogFilterConfig? = null,
    searchFocusRequester: FocusRequester? = null
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.logCount(visibleCount, totalCount),
            modifier = Modifier
                .background(colors.surfaceMuted, RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = colors.onSurface.copy(alpha = 0.68f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        if (onQueryChange != null) {
            LogSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
                focusRequester = searchFocusRequester
            )
        } else if (query.isNotBlank()) {
            Text(
                text = strings.searchPrefix(query),
                color = colors.primary.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (highlight != null || filter != null) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceMuted)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (highlight != null) {
                    LogHeaderToggle(Icons.Default.Highlight, strings.panelHighlight, highlight.enabled) {
                        HighlightEditor(highlight)
                    }
                }
                if (filter != null) {
                    LogHeaderToggle(Icons.Default.FilterList, strings.panelLog, filter.isActive) {
                        LogFilterOptionsEditor(filter)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceMuted)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogHeaderAction(Icons.Default.ContentCopy, strings.btnCopyLogs, onCopyLogs)
            LogHeaderAction(Icons.Default.Description, strings.btnExportText, onExportText)
            LogHeaderAction(Icons.Default.TableChart, strings.btnExportCsv, onExportCsv)
            LogHeaderAction(Icons.Default.DeleteSweep, strings.actionClearLogs, onClearLogs, destructive = true)
        }
    }
}

/** 日志头部图标按钮；destructive = true 时使用错误色。 */
@Composable
fun LogHeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val colors = LocalAppColors.current
    AppTooltip(description) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(17.dp),
                tint = if (destructive) colors.error.copy(alpha = 0.76f) else colors.onSurface.copy(alpha = 0.64f)
            )
        }
    }
}

/** 日志行内搜索框：直接编辑过滤词，右侧一键清空。 */
@Composable
fun LogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .height(30.dp)
            .background(colors.surfaceMuted, shape)
            .border(
                1.dp,
                if (query.isBlank()) colors.border.copy(alpha = 0.62f) else colors.primary.copy(alpha = 0.5f),
                shape
            )
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.onSurface.copy(alpha = if (query.isBlank()) 0.4f else 0.62f)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = if (focusRequester != null) {
                        Modifier.fillMaxWidth().focusRequester(focusRequester)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary)
                )
                if (query.isEmpty()) {
                    Text(
                        text = strings.searchLog,
                        fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = 0.34f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (query.isNotEmpty()) {
                AppTooltip(strings.btnClearSearch) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.btnClearSearch,
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") },
                        tint = colors.onSurface.copy(alpha = 0.52f)
                    )
                }
            }
        }
    }
}

/** 日志头部开关按钮：点击展开弹层，active 时图标使用主题色。 */
@Composable
private fun LogHeaderToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    content: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppTooltip(label) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(17.dp),
                    tint = if (active) colors.primary else colors.onSurface.copy(alpha = 0.64f)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(278.dp),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                content()
            }
        }
    }
}

/** 关键词高亮编辑器：启用/忽略大小写开关 + 关键词输入框。 */
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

/** 日志筛选选项（搜索词已移到头部输入框，这里只放显示策略与类型开关）。 */
@Composable
fun LogFilterOptionsEditor(filter: LogFilterConfig) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionItem(filter.onlyMatches, strings.optOnlyMatches, Modifier.fillMaxWidth()) { filter.onlyMatches = it }
        OptionItem(filter.autoScroll, strings.optAutoScroll, Modifier.fillMaxWidth()) { filter.autoScroll = it }
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

/** 单条日志行：时间戳 + 类型徽章 + 高亮内容，右键可复制。 */
@Composable
fun LogEntryRow(
    log: LogEntry,
    baseColor: Color,
    highlight: KeywordHighlightConfig,
    extraHighlight: String = ""
) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val rowText = "[${log.time}] ${log.type.name.padEnd(4)} ${log.content}"
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(strings.actionCopyRow) { DesktopActions.copyText(rowText) },
                ContextMenuItem(strings.actionCopyContent) { DesktopActions.copyText(log.content) }
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = log.time,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = colors.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(top = 1.dp)
            )

            LogTypeBadge(log.type)

            Box(modifier = Modifier.weight(1f)) {
                LogLine(log.content, baseColor, highlight, extraHighlight)
            }
        }
    }
}

/** 日志类型徽章（TX / RX / INFO）。 */
@Composable
fun LogTypeBadge(type: LogType) {
    val colors = LocalAppColors.current
    val (bgColor, textColor) = when (type) {
        LogType.TX -> colors.primary.copy(alpha = 0.14f) to colors.primary
        LogType.RX -> colors.success.copy(alpha = 0.14f) to colors.success
        LogType.INFO -> colors.onSurface.copy(alpha = 0.08f) to colors.onSurface.copy(alpha = 0.58f)
    }
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.name,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            lineHeight = 11.sp
        )
    }
}

/** 滚离底部时出现的"回到最新"悬浮气泡。 */
@Composable
fun ScrollToBottomPill(
    visible: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val colors = LocalAppColors.current
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.82f)),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(13.dp),
                    tint = colors.primary
                )
                Text(
                    text = "$count",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }
    }
}

/** 上下日志/发送区分隔条：可拖动调整比例，双击复位。 */
@Composable
fun PanelSplitter(
    onDragDelta: (Float) -> Unit,
    onReset: () -> Unit
) {
    val colors = LocalAppColors.current
    var isHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onReset() }
                )
            }
            .background(if (isHovered) colors.primary.copy(alpha = 0.08f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border.copy(alpha = 0.72f))
        )
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (isHovered) colors.primary else colors.border.copy(alpha = 0.9f))
        )
    }
}

/** 清空日志确认对话框。 */
@Composable
fun ConfirmClearLogsDialog(visible: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmClearLogsTitle) },
        text = { Text(strings.confirmClearLogsMessage) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.btnConfirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.btnCancel) } }
    )
}

/** 日志区空状态占位。 */
@Composable
fun EmptyLogState(message: String? = null) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = colors.onSurface.copy(alpha = 0.24f)
        )
        Text(
            text = message ?: strings.emptyLog,
            color = colors.onSurface.copy(alpha = 0.46f),
            fontSize = 12.sp
        )
    }
}

/** 单行日志文本，支持关键词高亮。 */
@Composable
fun LogLine(
    text: String,
    baseColor: Color,
    highlight: KeywordHighlightConfig,
    extraHighlight: String = ""
) {
    val keywords = remember(highlight.keywordsText) { highlight.keywords }
    val activeKeywords = remember(keywords, extraHighlight) {
        buildList {
            addAll(keywords)
            extraHighlight.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
    Text(
        text = if (highlight.enabled || extraHighlight.isNotBlank()) {
            buildHighlightedLogText(
                text = text,
                keywords = activeKeywords,
                ignoreCase = highlight.ignoreCase
            )
        } else {
            AnnotatedString(text)
        },
        color = baseColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
}

private fun buildHighlightedLogText(
    text: String,
    keywords: List<String>,
    ignoreCase: Boolean
): AnnotatedString {
    // 合并关键词和搜索词命中范围后统一着色，避免重叠区间重复拼接文本。
    val ranges = findKeywordRanges(text, keywords, ignoreCase)
    if (ranges.isEmpty()) return AnnotatedString(text)

    val style = SpanStyle(
        color = Color.Black,
        background = Color(0xFFFFE58F),
        fontWeight = FontWeight.Bold
    )
    return AnnotatedString(
        text = text,
        spanStyles = ranges.map { range ->
            AnnotatedString.Range(style, range.first, range.last + 1)
        }
    )
}

private fun findKeywordRanges(
    text: String,
    keywords: List<String>,
    ignoreCase: Boolean
): List<IntRange> {
    // 每次命中后至少推进一个字符，允许重叠匹配并避免空关键词造成死循环。
    val ranges = mutableListOf<IntRange>()
    keywords.forEach { keyword ->
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(keyword, startIndex = start, ignoreCase = ignoreCase)
            if (index < 0) break
            ranges += index until index + keyword.length
            start = index + keyword.length
        }
    }
    if (ranges.isEmpty()) return emptyList()

    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    sorted.forEach { range ->
        val last = merged.lastOrNull()
        if (last == null || range.first > last.last + 1) {
            merged += range
        } else if (range.last > last.last) {
            merged[merged.lastIndex] = last.first..range.last
        }
    }
    return merged
}
