package com.yiaobang.kserial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.theme.CupertinoTheme
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.ui.AppStatusBar
import com.yiaobang.kserial.ui.AppTopBar
import com.yiaobang.kserial.ui.ControlPanel

/**
 * 应用主入口视图
 * 职责：顶层布局编排，将各功能区组件组合在一起
 */
@Composable
fun App(
    viewModel: SerialViewModel,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onNewWindow: () -> Unit
) {
    val prefs = viewModel.prefs
    val listState = rememberLazyListState()

    // 日志自动滚动到最新一条
    LaunchedEffect(viewModel.logs.size) {
        if (viewModel.logs.isNotEmpty()) listState.animateScrollToItem(viewModel.logs.size - 1)
    }

    // 循环发送联动：串口状态或开关变化时自动启停
    LaunchedEffect(prefs.loopSendEnabled, viewModel.isPortOpen) {
        if (prefs.loopSendEnabled && viewModel.isPortOpen) {
            viewModel.startLoopSend()
        } else {
            viewModel.stopLoopSend()
        }
    }

    // 窗口关闭时强制回收资源
    DisposableEffect(Unit) {
        onDispose { viewModel.onCleared() }
    }

    CupertinoTheme(isDark = prefs.isDark) {
        val colors = LocalAppColors.current
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Column {
                // 顶部标题栏
                AppTopBar(
                    title = "KSerial",
                    isTop = prefs.isAlwaysOnTop,
                    onTop = { prefs.isAlwaysOnTop = !prefs.isAlwaysOnTop },
                    onLanguage = { prefs.switchLanguage() },
                    onTheme = { prefs.switchTheme() },
                    onMinimize = onMinimize,
                    onClose = onClose
                )

                // 主体区域
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    // 左侧：数据流展示面板
                    DataDisplayPanel(viewModel = viewModel, listState = listState, modifier = Modifier.weight(1f))

                    // 右侧：控制面板（可滚动）
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ControlPanel(vm = viewModel, onNewWindow = onNewWindow)
                    }
                }

                // 底部状态栏
                AppStatusBar(viewModel)
            }
        }
    }
}

/**
 * 左侧数据流展示面板（日志列表 + 发送输入框）
 */
@Composable
private fun DataDisplayPanel(
    viewModel: SerialViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {
    val colors = LocalAppColors.current
    val prefs = viewModel.prefs

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 接收区日志列表（占 70%）
        Box(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (prefs.isDark) Color(0xFF141414) else Color(0xFFF9F9F9))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(viewModel.logs) { log ->
                    Text(
                        text = "[${log.time}] ${if (log.type == LogType.TX) "->" else "<-"} ${log.content}",
                        color = when (log.type) {
                            LogType.TX -> colors.primary
                            LogType.RX -> colors.success
                            else -> colors.onSurface.copy(0.4f)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 发送区输入框（占 30%）
        Box(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
        ) {
            BasicTextField(
                value = viewModel.inputText,
                onValueChange = { viewModel.inputText = it },
                modifier = Modifier.fillMaxSize().padding(8.dp),
                textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.primary)
            )
        }
    }
}