package com.yiaobang.kserial.workspace

import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.model.AppSettingsStore
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStateTest {
    @AfterTest
    fun cleanupGlobals() {
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    @Test
    fun managesTabsDetachedWindowsAndMerging() = withWorkspace { workspace ->
        val firstBasic = assertNotNull(workspace.activeTab(workspace.mainHost))
        assertEquals(WorkspaceSection.Basic, firstBasic.section)
        assertEquals("KSerial", workspace.hostTitle(workspace.mainHost))

        val secondBasicId = workspace.openTab(WorkspaceSection.Basic)
        val simulatorId = workspace.openTab(WorkspaceSection.Simulator)
        val gatewayId = workspace.openTab(WorkspaceSection.Gateway)
        assertEquals("串口终端 2", workspace.tab(secondBasicId)?.title)
        assertNotNull(workspace.tab(simulatorId)?.serialViewModel)
        assertNotNull(workspace.tab(gatewayId)?.gatewayViewModel)

        workspace.selectSection(workspace.mainHost.id, WorkspaceSection.Simulator)
        workspace.selectTab(workspace.mainHost.id, simulatorId)
        workspace.detachTab(workspace.mainHost.id, simulatorId)
        val floating = workspace.floatingHosts.single()
        assertEquals(simulatorId, workspace.activeTab(floating)?.id)
        assertTrue(workspace.hostTitle(floating).startsWith("KSerial -"))

        workspace.mergeTabToMain(floating.id, simulatorId)
        assertTrue(workspace.floatingHosts.isEmpty())
        assertEquals(simulatorId, workspace.activeTab(workspace.mainHost)?.id)

        workspace.detachTab(workspace.mainHost.id, gatewayId)
        val gatewayHost = workspace.floatingHosts.single()
        workspace.mergeHostToMain(gatewayHost.id)
        assertTrue(workspace.floatingHosts.isEmpty())

        workspace.closeTab(workspace.mainHost.id, gatewayId)
        assertNull(workspace.tab(gatewayId))
    }

    @Test
    fun propagatesAppearanceAndPersistsGlobalState() = withWorkspace { workspace ->
        val basic = assertNotNull(workspace.activeTab(workspace.mainHost)?.serialViewModel)
        val simulatorId = workspace.openTab(WorkspaceSection.Simulator)
        val gatewayId = workspace.openTab(WorkspaceSection.Gateway)
        val simulator = assertNotNull(workspace.tab(simulatorId)?.serialViewModel)
        val gateway = assertNotNull(workspace.tab(gatewayId)?.gatewayViewModel)
        val initialLanguage = workspace.prefs.currentLanguage

        workspace.switchTheme()
        workspace.switchLanguage()
        workspace.toggleAlwaysOnTop()

        assertTrue(workspace.prefs.isDark)
        assertTrue(basic.prefs.isDark)
        assertTrue(simulator.prefs.isDark)
        assertEquals(initialLanguage.next(), gateway.language)
        assertTrue(workspace.prefs.isAlwaysOnTop)

        basic.config.selectedPort = "COM77"
        basic.config.baudRate = 230400
        workspace.sendHistory.record("AT\\r\\n")
        workspace.saveSettings().getOrThrow()
    }

    private fun withWorkspace(block: (WorkspaceState) -> Unit) {
        val directory = createTempDirectory("kserial-workspace-test")
        val path = directory.resolve("settings.conf")
        val workspace = WorkspaceState(AppSettingsStore(path))
        try {
            block(workspace)
            val persisted = AppSettingsStore(path).load().settings
            assertFalse(persisted.preferences.quickCommandsText.isBlank())
        } finally {
            workspace.disposeAll()
            directory.toFile().deleteRecursively()
        }
    }
}
