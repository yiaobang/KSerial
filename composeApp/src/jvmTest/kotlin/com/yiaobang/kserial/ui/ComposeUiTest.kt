package com.yiaobang.kserial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.WorkspaceApp
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.i18n.ProvideAppStrings
import com.yiaobang.kserial.model.AppSettingsStore
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.theme.CupertinoTheme
import com.yiaobang.kserial.theme.CupertinoLightColors
import com.yiaobang.kserial.workspace.WorkspaceSection
import com.yiaobang.kserial.workspace.WorkspaceState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ComposeUiTest {
    @Test
    fun workspaceNavigationCreatesIndependentSimulatorAndBridgePages() = runComposeUiTest {
        val settingsPath = Files.createTempDirectory("kserial-workspace-ui").resolve("settings.conf")
        val workspace = WorkspaceState(AppSettingsStore(settingsPath)).apply {
            prefs.currentLanguage = Language.EN
        }
        try {
            setContent {
                // 固定为桌面应用的最小可用视口，避免侧栏或控制面板因测试窗口过小被裁剪。
                Box(Modifier.size(1280.dp, 800.dp)) {
                    WorkspaceApp(workspace, workspace.mainHost, onClose = {}, onMinimize = {})
                }
            }

            onNodeWithContentDescription("Device Simulator").performClick()
            onNodeWithText("Load Config").assertExists()
            onNodeWithText("Run").assertExists()
            onNodeWithText("Stop").assertExists()
            onNodeWithText("Quick Send").assertDoesNotExist()
            runOnIdle {
                assertEquals(WorkspaceSection.Simulator, workspace.mainHost.selectedSection)
                assertEquals(1, workspace.tabIdsFor(workspace.mainHost).size)
            }

            onNodeWithContentDescription("Device Simulator").performClick()
            runOnIdle {
                assertEquals(1, workspace.tabIdsFor(workspace.mainHost).size)
            }

            onNodeWithContentDescription("Serial Bridge").performClick()
            onNodeWithText("Load Config").assertExists()
            onNodeWithText("Run").assertExists()
            onNodeWithText("Stop").assertExists()
            onNodeWithText("Forwarding Rules").assertDoesNotExist()
            runOnIdle {
                assertEquals(WorkspaceSection.Gateway, workspace.mainHost.selectedSection)
                assertEquals(1, workspace.tabIdsFor(workspace.mainHost).size)
            }

            onNodeWithContentDescription("Serial Console").performClick()
            onNodeWithText("Connection & Settings").assertExists()
            runOnIdle {
                assertEquals(WorkspaceSection.Basic, workspace.mainHost.selectedSection)
            }
        } finally {
            workspace.disposeAll()
        }
    }

    @Test
    fun protocolToolsCalculateFrameAndAppendSelectedChecksum() = runComposeUiTest {
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false).apply {
            prefs.hexSend = true
            inputText = "01 02 03 04 05 06 07 08 09"
        }
        try {
            setContent {
                TestTheme {
                    Box(Modifier.width(360.dp)) { ProtocolToolsPanel(viewModel) }
                }
            }

            onNodeWithText("1 complete frames · 1 remaining bytes").assertExists()
            onNodeWithText("CRC16-Modbus").performClick()
            onNodeWithText("XOR").performClick()
            onNodeWithText("Result: 01").assertExists()
            onNodeWithText("Append Checksum").performClick()
            runOnIdle {
                assertEquals("01 02 03 04 05 06 07 08 09 01", viewModel.inputText)
            }
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun topBarExposesEveryWindowActionAndInvokesCallbacks() = runComposeUiTest {
        var pinClicks by mutableIntStateOf(0)
        var languageClicks by mutableIntStateOf(0)
        var themeClicks by mutableIntStateOf(0)
        var minimizeClicks by mutableIntStateOf(0)
        var closeClicks by mutableIntStateOf(0)
        setContent {
            TestTheme {
                AppTopBar(
                    title = "KSerial Test",
                    isTop = true,
                    onTop = { pinClicks++ },
                    onLanguage = { languageClicks++ },
                    onTheme = { themeClicks++ },
                    onMinimize = { minimizeClicks++ },
                    onClose = { closeClicks++ }
                )
            }
        }

        onNodeWithText("KSerial Test").assertExists()
        onNodeWithContentDescription("Keep window on top").performClick()
        onNodeWithContentDescription("Change language").performClick()
        onNodeWithContentDescription("Change theme").performClick()
        onNodeWithContentDescription("Minimize window").performClick()
        onNodeWithContentDescription("Close window").performClick()

        runOnIdle {
            assertEquals(listOf(1, 1, 1, 1, 1), listOf(pinClicks, languageClicks, themeClicks, minimizeClicks, closeClicks))
        }
    }

    @Test
    fun expandableCardHidesAndRevealsItsContent() = runComposeUiTest {
        setContent {
            TestTheme {
                ExpandableCardPanel(title = "Advanced") {
                    androidx.compose.material3.Text("Advanced content")
                }
            }
        }

        onNodeWithText("Advanced content").assertDoesNotExist()
        onNodeWithContentDescription("Expand").assertExists()
        onNodeWithText("Advanced").performClick()
        onNodeWithText("Advanced content").assertExists()
        onNodeWithContentDescription("Collapse").assertExists()
        onNodeWithText("Advanced").performClick()
        onNodeWithText("Advanced content").assertDoesNotExist()
    }

    @Test
    fun actionButtonHonorsEnabledState() = runComposeUiTest {
        var enabledClicks by mutableIntStateOf(0)
        var disabledClicks by mutableIntStateOf(0)
        setContent {
            TestTheme {
                Column {
                    ActionButton("Enabled", CupertinoLightColors.primary, Modifier.fillMaxWidth()) { enabledClicks++ }
                    ActionButton("Disabled", CupertinoLightColors.surface, Modifier.fillMaxWidth(), enabled = false) { disabledClicks++ }
                }
            }
        }

        onNodeWithText("Enabled").assertIsEnabled().performClick()
        onNodeWithText("Disabled").assertIsNotEnabled()
        runOnIdle {
            assertEquals(1, enabledClicks)
            assertEquals(0, disabledClicks)
        }
    }

    @Test
    fun serialControlPanelExposesReceiveFramingReconnectAndLineControls() = runComposeUiTest {
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            setContent {
                TestTheme {
                    Box(Modifier.width(360.dp)) { ControlPanel(viewModel) }
                }
            }

            // 终端页的端点行现在是「地址输入框 + 检测到的串口下拉」，
            // 地址框既接受 COM3 也接受 tcp:// 这类网络地址。
            onNodeWithText("Address").assertExists()
            onNodeWithText("Detected ports").assertExists()
            onNodeWithText("COM3 or tcp://host:port").assertExists()
            onNodeWithText("Receive Display").performClick()
            onNodeWithText("Receive Framing").assertExists()
            onNodeWithText("Time Window").assertExists()
            onNodeWithText("Line Control").performClick()
            onNodeWithText("DTR").assertExists()
            onNodeWithText("RTS").assertExists()
            onNodeWithText("BREAK").assertExists()
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun statusBarShowsConnectionAndProvidesAccessibleCounterResetActions() = runComposeUiTest {
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false).apply {
            config.selectedPort = "COM42"
            txCount = 7
            rxCount = 9
        }
        try {
            setContent { TestTheme { AppStatusBar(viewModel) } }

            onNodeWithText("Disconnected").assertExists()
            onNodeWithText("COM42").assertExists()
            onNodeWithContentDescription("TX Count 7").performClick()
            onNodeWithContentDescription("RX Count 9").performClick()
            runOnIdle {
                assertEquals(0L, viewModel.txCount)
                assertEquals(0L, viewModel.rxCount)
            }
            onNodeWithContentDescription("TX Count 0").assertExists()
            onNodeWithContentDescription("RX Count 0").assertExists()
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun emptyStatusBarDoesNotRepeatThePortSelectionPlaceholder() = runComposeUiTest {
        val viewModel = SerialViewModel(autoConnectFirstPort = false, syncOnConfigChange = false)
        try {
            setContent { TestTheme { AppStatusBar(viewModel) } }

            onNodeWithText("Disconnected").assertExists()
            onNodeWithText("Not selected").assertDoesNotExist()
        } finally {
            viewModel.onCleared()
            PortMonitor.stop()
            SerialPortLeaseRegistry.clear()
        }
    }

    @Test
    fun inspectorCanCollapseAndRestoreWithoutCoveringTheWorkspace() = runComposeUiTest {
        val settingsPath = Files.createTempDirectory("kserial-inspector-ui").resolve("settings.conf")
        val workspace = WorkspaceState(AppSettingsStore(settingsPath)).apply {
            prefs.currentLanguage = Language.EN
        }
        try {
            setContent {
                Box(Modifier.size(1120.dp, 760.dp)) {
                    WorkspaceApp(workspace, workspace.mainHost, onClose = {}, onMinimize = {})
                }
            }

            onNodeWithText("Control Center").assertExists()
            onNodeWithContentDescription("Hide controls").performClick()
            onNodeWithText("Control Center").assertDoesNotExist()
            onNodeWithContentDescription("Show controls").performClick()
            onNodeWithText("Control Center").assertExists()
        } finally {
            workspace.disposeAll()
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        ProvideAppStrings(Language.EN) {
            CupertinoTheme(isDark = false, content = content)
        }
    }
}
