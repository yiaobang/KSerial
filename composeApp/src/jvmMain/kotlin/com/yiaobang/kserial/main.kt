package com.yiaobang.kserial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yiaobang.kserial.resources.Res
import com.yiaobang.kserial.resources.app_icon
import com.yiaobang.kserial.workspace.WorkspaceState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.awt.Point
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds
import java.awt.Window as AwtWindow
import kotlin.system.exitProcess

/**
 * 程序入口：带参数时进入无头模式（gateway / simulate / test），
 * 不带参数才启动 GUI。这样同一个安装包既能当桌面工具，也能塞进 CI 脚本。
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        exitProcess(com.yiaobang.kserial.cli.runCli(args))
    }
    startGui()
}

/** Compose Desktop 入口：创建唯一工作区，并为主窗口和浮动窗口绑定各自宿主状态。 */
private fun startGui() = application {
    val workspace = remember { WorkspaceState() }
    val appIcon = painterResource(Res.drawable.app_icon)

    // key 保证宿主身份变化时窗口级 remember 状态不会错误复用。
    key(workspace.mainHost.id) {
        val mainWindowState = rememberWindowState(
            width = 1120.dp,
            height = 760.dp,
            position = WindowPosition(Alignment.Center)
        )

        Window(
            onCloseRequest = {
                workspace.disposeAll()
                exitApplication()
            },
            state = mainWindowState,
            title = workspace.hostTitle(workspace.mainHost),
            icon = appIcon,
            undecorated = true,
            transparent = true,
            alwaysOnTop = workspace.prefs.isAlwaysOnTop,
            resizable = true
        ) {
            InstallWindowBehavior(window, workspace)
            WorkspaceApp(
                workspace = workspace,
                host = workspace.mainHost,
                onClose = {
                    workspace.disposeAll()
                    exitApplication()
                },
                onMinimize = { window.isMinimized = true }
            )
        }
    }

    // 浮动窗口关闭时标签合并回主窗口，而不是销毁仍在使用的串口会话。
    workspace.floatingHosts.forEach { host ->
        key(host.id) {
            val floatingWindowState = rememberWindowState(
                width = 980.dp,
                height = 700.dp,
                position = WindowPosition(Alignment.Center)
            )

            Window(
                onCloseRequest = { workspace.mergeHostToMain(host.id) },
                state = floatingWindowState,
                title = workspace.hostTitle(host),
                icon = appIcon,
                undecorated = true,
                transparent = true,
                alwaysOnTop = workspace.prefs.isAlwaysOnTop,
                resizable = true
            ) {
                InstallWindowBehavior(window, workspace)
                WorkspaceApp(
                    workspace = workspace,
                    host = host,
                    onClose = { workspace.mergeHostToMain(host.id) },
                    onMinimize = { window.isMinimized = true }
                )
            }
        }
    }
}

@Composable
private fun InstallWindowBehavior(
    window: AwtWindow,
    workspace: WorkspaceState
) {
    // 部分桌面环境切换置顶后需要主动前置一次才能立即生效。
    LaunchedEffect(workspace.prefs.isAlwaysOnTop) {
        if (workspace.prefs.isAlwaysOnTop) {
            delay(50L.milliseconds)
            window.isAlwaysOnTop = true
            window.toFront()
            window.requestFocus()
        } else {
            window.isAlwaysOnTop = false
        }
    }

    DisposableEffect(window) {
        window.minimumSize = Dimension(900, 620)
        // 窗口采用无系统边框样式，顶部 48px 自行承担标题栏拖动行为。
        val adapter = object : MouseAdapter() {
            private var dragStart = Point()
            private var dragging = false

            override fun mousePressed(e: MouseEvent) {
                dragging = e.y <= 48
                if (dragging) dragStart = e.point
            }

            override fun mouseDragged(e: MouseEvent) {
                if (dragging) {
                    val loc = window.location
                    window.setLocation(loc.x + e.x - dragStart.x, loc.y + e.y - dragStart.y)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                dragging = false
            }
        }
        window.addMouseListener(adapter)
        window.addMouseMotionListener(adapter)
        onDispose {
            window.removeMouseListener(adapter)
            window.removeMouseMotionListener(adapter)
        }
    }
}
