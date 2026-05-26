package com.yiaobang.kserial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yiaobang.kserial.workspace.WindowHostState
import com.yiaobang.kserial.workspace.WorkspaceState
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Window as AwtWindow
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

fun main() = application {
    val workspace = remember { WorkspaceState() }

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
            undecorated = true,
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
                undecorated = true,
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
    LaunchedEffect(workspace.prefs.isAlwaysOnTop) {
        if (workspace.prefs.isAlwaysOnTop) {
            delay(50L)
            window.isAlwaysOnTop = true
            window.toFront()
            window.requestFocus()
        } else {
            window.isAlwaysOnTop = false
        }
    }

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
        adapter
    }
}
