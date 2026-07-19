package com.yiaobang.kserial.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.model.AppPreferences
import com.yiaobang.kserial.model.AppSettingsStore
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.model.PersistedSettings
import com.yiaobang.kserial.model.SendHistoryState
import com.yiaobang.kserial.model.applyPersisted
import com.yiaobang.kserial.model.toPersisted
import com.yiaobang.kserial.model.toPersistedAppearance
import com.yiaobang.kserial.model.toPersistedPreferences
import com.yiaobang.kserial.serial.PortMonitor
import com.yiaobang.kserial.serial.SerialPortLeaseRegistry
import com.yiaobang.kserial.utils.AppPaths

private const val MAIN_HOST_ID = 0

enum class WorkspaceSection(val label: String) {
    Basic("串口终端"),
    Simulator("设备模拟"),
    Gateway("串口桥接")
}

class AppTab(
    val id: Int,
    val section: WorkspaceSection,
    val title: String,
    val titleIndex: Int,
    val serialViewModel: SerialViewModel? = null,
    val gatewayViewModel: GatewayViewModel? = null
)

class WindowHostState(
    val id: Int
) {
    val tabIds = mutableStateListOf<Int>()
    val activeTabBySection = mutableStateMapOf<WorkspaceSection, Int?>()
    var selectedSection by mutableStateOf(WorkspaceSection.Basic)
}

class WorkspaceState(
    private val settingsStore: AppSettingsStore = AppSettingsStore()
) {
    private val configDirectoryResult = AppPaths.initializeConfigDirectory()
    private val settingsLoadResult = settingsStore.load()
    private val initialSettings = settingsLoadResult.settings
    val prefs = AppPreferences().apply {
        applyPersisted(initialSettings.appearance, initialSettings.preferences)
    }
    val sendHistory = SendHistoryState().apply { restore(initialSettings.sendHistory) }
    val mainHost = WindowHostState(MAIN_HOST_ID)
    val floatingHosts = mutableStateListOf<WindowHostState>()

    private val tabs = mutableStateMapOf<Int, AppTab>()
    private val sectionCounts = mutableMapOf<WorkspaceSection, Int>()
    private var nextTabId = 1
    private var nextHostId = 1

    init {
        val tabId = openTab(WorkspaceSection.Basic, mainHost.id, autoConnectBasic = false)
        configDirectoryResult.exceptionOrNull()?.let { error ->
            tab(tabId)?.serialViewModel?.addLog(
                "Unable to initialize ${AppPaths.CONF_FILE_PATH.absolutePath}: ${error.message ?: error::class.simpleName}",
                LogType.INFO
            )
        }
        settingsLoadResult.warning?.let { warning ->
            tab(tabId)?.serialViewModel?.addLog(warning, LogType.INFO)
        }
        saveSettings().exceptionOrNull()?.let { error ->
            tab(tabId)?.serialViewModel?.addLog(
                "Unable to save ${settingsStore.settingsFile}: ${error.message ?: error::class.simpleName}",
                LogType.INFO
            )
        }
    }

    fun tab(tabId: Int?): AppTab? = tabId?.let { tabs[it] }

    fun tabIdsFor(host: WindowHostState): List<Int> {
        return host.tabIds.filter { tab(it)?.section == host.selectedSection }
    }

    fun activeTab(host: WindowHostState): AppTab? {
        return tab(host.activeTabBySection[host.selectedSection])
    }

    fun tabTitle(tab: AppTab): String {
        val selectedPort = tab.serialViewModel?.config?.selectedPort
        return if (!selectedPort.isNullOrBlank()) selectedPort else tab.title
    }

    fun hostTitle(host: WindowHostState): String {
        if (host.id == MAIN_HOST_ID) return "KSerial"
        val activeTitle = activeTab(host)?.let { tabTitle(it) } ?: host.selectedSection.label
        return "KSerial - $activeTitle"
    }

    fun openTab(
        section: WorkspaceSection,
        hostId: Int = MAIN_HOST_ID,
        autoConnectBasic: Boolean = false
    ): Int {
        val host = findHost(hostId) ?: mainHost
        val id = nextTabId++
        val titleIndex = nextSectionTitleIndex(section)
        val tab = AppTab(
            id = id,
            section = section,
            title = fallbackTabTitle(section, titleIndex),
            titleIndex = titleIndex,
            serialViewModel = when (section) {
                WorkspaceSection.Basic -> createSerialViewModel(
                    autoConnectFirstPort = autoConnectBasic,
                    syncOnConfigChange = true
                )
                WorkspaceSection.Simulator -> createSerialViewModel(
                    autoConnectFirstPort = false,
                    syncOnConfigChange = false
                )
                WorkspaceSection.Gateway -> null
            },
            gatewayViewModel = if (section == WorkspaceSection.Gateway) {
                GatewayViewModel().apply { language = prefs.currentLanguage }
            } else {
                null
            }
        )
        tabs[id] = tab
        host.tabIds += id
        host.selectedSection = section
        host.activeTabBySection[section] = id
        return id
    }

    fun openSelectedSectionTab(hostId: Int): Int {
        val host = findHost(hostId) ?: mainHost
        return openTab(host.selectedSection, host.id)
    }

    fun selectSection(hostId: Int, section: WorkspaceSection) {
        val host = findHost(hostId) ?: return
        host.selectedSection = section
    }

    fun selectTab(hostId: Int, tabId: Int) {
        val host = findHost(hostId) ?: return
        val tab = tab(tabId) ?: return
        if (tabId in host.tabIds && tab.section == host.selectedSection) {
            host.activeTabBySection[host.selectedSection] = tabId
        }
    }

    fun closeTab(hostId: Int, tabId: Int) {
        val host = findHost(hostId) ?: return
        val tab = tab(tabId) ?: return
        val sectionTabIds = tabIdsFor(host).filter { it != tabId }

        host.tabIds.remove(tabId)
        disposeTab(tabId)

        if (host.activeTabBySection[tab.section] == tabId) {
            host.activeTabBySection[tab.section] = sectionTabIds.firstOrNull()
        }

        if (host.id != MAIN_HOST_ID && host.tabIds.isEmpty()) {
            floatingHosts.remove(host)
        }

        if (mainHost.tabIds.isEmpty() && floatingHosts.isEmpty()) {
            openTab(WorkspaceSection.Basic, mainHost.id, autoConnectBasic = false)
        }
    }

    fun detachTab(hostId: Int, tabId: Int) {
        val source = findHost(hostId) ?: return
        val tab = tab(tabId) ?: return
        if (tabId !in source.tabIds) return
        if (source.id != MAIN_HOST_ID && source.tabIds.size == 1) return

        val remainingSectionTabs = tabIdsFor(source).filter { it != tabId }
        source.tabIds.remove(tabId)
        if (source.activeTabBySection[tab.section] == tabId) {
            source.activeTabBySection[tab.section] = remainingSectionTabs.firstOrNull()
        }

        val newHost = WindowHostState(nextHostId++)
        newHost.tabIds += tabId
        newHost.selectedSection = tab.section
        newHost.activeTabBySection[tab.section] = tabId
        floatingHosts += newHost

        if (source.id != MAIN_HOST_ID && source.tabIds.isEmpty()) {
            floatingHosts.remove(source)
        }
    }

    fun mergeTabToMain(hostId: Int, tabId: Int) {
        val source = findHost(hostId) ?: return
        val tab = tab(tabId) ?: return
        if (source.id == MAIN_HOST_ID || tabId !in source.tabIds) return

        val remainingSectionTabs = tabIdsFor(source).filter { it != tabId }
        source.tabIds.remove(tabId)
        if (source.activeTabBySection[tab.section] == tabId) {
            source.activeTabBySection[tab.section] = remainingSectionTabs.firstOrNull()
        }

        mainHost.tabIds += tabId
        mainHost.selectedSection = tab.section
        mainHost.activeTabBySection[tab.section] = tabId

        if (source.tabIds.isEmpty()) floatingHosts.remove(source)
    }

    fun mergeHostToMain(hostId: Int) {
        val source = findHost(hostId) ?: return
        if (source.id == MAIN_HOST_ID) return

        val movingTabs = source.tabIds.toList()
        source.tabIds.clear()
        mainHost.tabIds.addAll(movingTabs)
        movingTabs.lastOrNull()?.let { tabId ->
            val tab = tab(tabId) ?: return@let
            mainHost.selectedSection = tab.section
            mainHost.activeTabBySection[tab.section] = tabId
        }
        floatingHosts.remove(source)
    }

    fun switchTheme() {
        prefs.switchTheme()
        tabs.values.forEach { tab ->
            tab.serialViewModel?.prefs?.isDark = prefs.isDark
        }
    }

    fun switchLanguage() {
        prefs.switchLanguage()
        tabs.values.forEach { tab ->
            tab.serialViewModel?.prefs?.currentLanguage = prefs.currentLanguage
            tab.gatewayViewModel?.language = prefs.currentLanguage
        }
    }

    fun toggleAlwaysOnTop() {
        prefs.isAlwaysOnTop = !prefs.isAlwaysOnTop
    }

    fun disposeAll() {
        saveSettings()
        tabs.values.toList().forEach { disposeTab(it.id) }
        tabs.clear()
        mainHost.tabIds.clear()
        floatingHosts.clear()
        PortMonitor.stop()
        SerialPortLeaseRegistry.clear()
    }

    private fun createSerialViewModel(
        autoConnectFirstPort: Boolean,
        syncOnConfigChange: Boolean
    ): SerialViewModel {
        return SerialViewModel(
            autoConnectFirstPort = autoConnectFirstPort,
            syncOnConfigChange = syncOnConfigChange,
            sendHistory = sendHistory
        ).also {
            val source = tabs.values.lastOrNull { tab -> tab.serialViewModel != null }?.serialViewModel
            if (source != null) {
                it.config.applyPersisted(source.config.toPersisted())
                it.prefs.applyPersisted(prefs.toPersistedAppearance(), source.prefs.toPersistedPreferences())
            } else {
                it.config.applyPersisted(initialSettings.serial)
                it.prefs.applyPersisted(initialSettings.appearance, initialSettings.preferences)
            }
            it.prefs.currentLanguage = prefs.currentLanguage
            it.prefs.isDark = prefs.isDark
        }
    }

    fun saveSettings(): Result<java.nio.file.Path> {
        val source = tab(mainHost.activeTabBySection[WorkspaceSection.Basic])?.serialViewModel
            ?: tabs.values.firstNotNullOfOrNull { it.serialViewModel }
        val settings = PersistedSettings(
            appearance = prefs.toPersistedAppearance(),
            serial = source?.config?.toPersisted() ?: initialSettings.serial,
            preferences = source?.prefs?.toPersistedPreferences() ?: initialSettings.preferences,
            sendHistory = sendHistory.snapshot()
        )
        return settingsStore.save(settings)
    }

    private fun nextSectionTitleIndex(section: WorkspaceSection): Int {
        val count = (sectionCounts[section] ?: 0) + 1
        sectionCounts[section] = count
        return count
    }

    private fun fallbackTabTitle(section: WorkspaceSection, titleIndex: Int): String {
        return if (titleIndex == 1) section.label else "${section.label} $titleIndex"
    }

    private fun disposeTab(tabId: Int) {
        val tab = tabs.remove(tabId) ?: return
        tab.serialViewModel?.onCleared()
        tab.gatewayViewModel?.onCleared()
    }

    private fun findHost(hostId: Int): WindowHostState? {
        if (hostId == MAIN_HOST_ID) return mainHost
        return floatingHosts.firstOrNull { it.id == hostId }
    }
}
