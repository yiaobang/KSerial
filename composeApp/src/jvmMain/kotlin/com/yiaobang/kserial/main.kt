package com.yiaobang.kserial

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * 窗口信息数据结构
 */
data class WindowInfo(val id: Int)

/**
 * 应用启动入口
 * 维护多窗口列表，每个窗口持有独立的 ViewModel 实例
 */
fun main() = application {
    val windows = remember { mutableStateListOf(WindowInfo(0)) }
    var nextId by remember { mutableStateOf(1) }

    windows.forEach { windowInfo ->
        val windowState = rememberWindowState(
            width = 920.dp,
            height = 720.dp,
            position = WindowPosition(Alignment.Center)
        )
        // ViewModel 提升到 Window 作用域外，确保 alwaysOnTop 状态与 Window 属性同步
        val viewModel = remember { SerialViewModel() }

        Window(
            onCloseRequest = {
                windows.remove(windowInfo)
                if (windows.isEmpty()) exitApplication()
            },
            state = windowState,
            title = "KSerial",
            undecorated = true,
            alwaysOnTop = viewModel.prefs.isAlwaysOnTop,
            resizable = true
        ) {
            // 双保险置顶：通过 AWT 句柄直接控制
            LaunchedEffect(viewModel.prefs.isAlwaysOnTop) {
                if (viewModel.prefs.isAlwaysOnTop) {
                    delay(50L)
                    window.isAlwaysOnTop = true
                    window.toFront()
                    window.requestFocus()
                } else {
                    window.isAlwaysOnTop = false
                }
            }

            // AWT 鼠标监听器：实现顶部 40px 区域的窗口拖拽
            remember(window) {
                val adapter = object : MouseAdapter() {
                    private var dragStart = Point()
                    override fun mousePressed(e: MouseEvent) {
                        if (e.y <= 40) dragStart = e.point
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        if (e.y <= 40) {
                            val loc = window.location
                            window.setLocation(loc.x + e.x - dragStart.x, loc.y + e.y - dragStart.y)
                        }
                    }
                }
                window.addMouseListener(adapter)
                window.addMouseMotionListener(adapter)
            }

            App(
                viewModel = viewModel,
                onClose = {
                    windows.remove(windowInfo)
                    if (windows.isEmpty()) exitApplication()
                },
                onMinimize = { window.isMinimized = true },
                onNewWindow = { windows.add(WindowInfo(nextId++)) }
            )
        }
    }
}