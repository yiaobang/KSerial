package com.yiaobang.kserial.ui

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.theme.LocalAppColors

// 页面骨架组件：主内容 + 可收起的右侧检查器面板，供三个工作区共用。

/** 页面主体：宽窗口时主区与检查器并排，窄窗口时检查器改为上下布局。 */
@Composable
fun AdaptivePageBody(
    sideWidth: Dp,
    modifier: Modifier = Modifier,
    mainContent: @Composable (Modifier) -> Unit,
    sideContent: @Composable ColumnScope.() -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val narrow = availableWidth < 720.dp
        if (narrow) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (controlsVisible) {
                    InspectorPanel(
                        width = availableWidth,
                        modifier = Modifier.weight(1f),
                        onClose = { controlsVisible = false },
                        content = sideContent
                    )
                } else {
                    CompactInspectorToggle(
                        controlsVisible = controlsVisible,
                        onToggle = { controlsVisible = true }
                    )
                    mainContent(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                mainContent(Modifier.weight(1f).padding(end = 10.dp))
                if (controlsVisible) {
                    InspectorPanel(
                        width = sideWidth,
                        onClose = { controlsVisible = false },
                        content = sideContent
                    )
                } else {
                    CollapsedInspectorRail(onToggle = { controlsVisible = true })
                }
            }
        }
    }
}

/** 展开的检查器是一个连续表面，标题、折叠动作和内部滚动属于同一层级。 */
@Composable
fun InspectorPanel(
    width: Dp,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .then(modifier)
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.panelControls,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface.copy(alpha = 0.82f)
            )
            AppTooltip(strings.actionHideControls) {
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = strings.actionHideControls,
                        modifier = Modifier.size(17.dp),
                        tint = colors.onSurface.copy(alpha = 0.52f)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.72f))
        ScrollableSidePanel(width = width, modifier = Modifier.weight(1f), content = content)
    }
}

/** 控制项可能超过窗口高度，始终显示滚动条提示下方还有更多设置。 */
@Composable
fun ScrollableSidePanel(
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 12.dp)
                .verticalScroll(scrollState),
            content = content
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(6.dp),
            style = ScrollbarStyle(
                minimalHeight = 36.dp,
                thickness = 6.dp,
                shape = RoundedCornerShape(3.dp),
                hoverDurationMillis = 180,
                unhoverColor = colors.border.copy(alpha = 0.62f),
                hoverColor = colors.primary.copy(alpha = 0.72f)
            )
        )
    }
}

/** 仅在检查器收起时保留窄轨道，避免正常状态下浪费工作区宽度。 */
@Composable
fun CollapsedInspectorRail(onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.82f), RoundedCornerShape(9.dp))
    ) {
        AppTooltip(strings.actionShowControls) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = strings.actionShowControls,
                    modifier = Modifier.size(17.dp),
                    tint = colors.primary
                )
            }
        }
    }
}

/** 窄窗口使用完整的面板切换条，避免图标覆盖正文或失去上下文。 */
@Composable
fun CompactInspectorToggle(
    controlsVisible: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val label = if (controlsVisible) strings.actionHideControls else strings.actionShowControls
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (controlsVisible) Icons.Default.ChevronRight else Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = colors.primary
        )
        Text(label, color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
