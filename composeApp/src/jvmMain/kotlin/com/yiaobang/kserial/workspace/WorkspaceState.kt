package com.yiaobang.kserial.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.feature.CaptureViewModel
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.feature.PlotterViewModel
import com.yiaobang.kserial.feature.TestRunnerViewModel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAIN_HOST_ID = 0

/**
 * 一个桌面窗口的轻量状态。ViewModel 归标签页所有，窗口只保存标签顺序和选择状态，
 * 因此标签脱离或合并窗口时不会断开串口连接。
 */
class WindowHostState(
    val id: Int
) {
    val tabIds = mutableStateListOf<Int>()
    val activeTabBySection = mutableStateMapOf<WorkspaceSection, Int?>()
    var selectedSection by mutableStateOf(WorkspaceSection.Basic)
}

/**
 * 应用级工作区协调器，负责多标签/多窗口生命周期、全局外观偏好和配置持久化。
 * 所有标签页 ViewModel 都由这里创建和释放，避免 Compose 重组意外重建串口会话。
 */
class WorkspaceState(
    private val settingsStore: AppSettingsStore = AppSettingsStore()
) {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        // 始终保留一个基础终端作为首次启动入口，同时把初始化问题显示在页面日志中。
        val tabId = openTab(WorkspaceSection.Basic, mainHost.id, autoConnectBasic = false)
        configDirectoryResult.exceptionOrNull()?.let { error ->
            (tab(tabId)?.content as? SerialWorkspaceTabContent)?.viewModel?.addLog(
                "Unable to initialize ${AppPaths.CONF_FILE_PATH.absolutePath}: ${error.message ?: error::class.simpleName}",
                LogType.INFO
            )
        }
        settingsLoadResult.warning?.let { warning ->
            (tab(tabId)?.content as? SerialWorkspaceTabContent)?.viewModel?.addLog(warning, LogType.INFO)
        }
        saveSettings().exceptionOrNull()?.let { error ->
            (tab(tabId)?.content as? SerialWorkspaceTabContent)?.viewModel?.addLog(
                "Unable to save ${settingsStore.settingsFile}: ${error.message ?: error::class.simpleName}",
                LogType.INFO
            )
        }
        // 用户偏好和串口参数在变化停止后自动保存；输入内容与循环发送仍明确不持久化。
        persistenceScope.launch {
            snapshotFlow { persistedSettingsSnapshot() }
                .drop(1)
                .collectLatest { settings ->
                    delay(750)
                    withContext(Dispatchers.IO) { settingsStore.save(settings) }
                }
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
        val selectedPort = tab.content.selectedPort
        return if (!selectedPort.isNullOrBlank()) selectedPort else tab.title
    }

    fun hostTitle(host: WindowHostState): String {
        if (host.id == MAIN_HOST_ID) return "KSerial"
        val activeTitle = activeTab(host)?.let { tabTitle(it) } ?: host.selectedSection.localizedTitle(prefs.currentLanguage)
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
        // 终端和模拟器复用串口 VM；桥接拥有多端点，必须使用专门的 GatewayViewModel。
        val content = when (section) {
            WorkspaceSection.Basic -> BasicTabContent(
                createSerialViewModel(
                    autoConnectFirstPort = autoConnectBasic,
                    syncOnConfigChange = true
                )
            )
            WorkspaceSection.Simulator -> SimulatorTabContent(
                createSerialViewModel(
                    autoConnectFirstPort = false,
                    syncOnConfigChange = false
                )
            )
            WorkspaceSection.Gateway -> GatewayTabContent(
                GatewayViewModel().apply { language = prefs.currentLanguage }
            )
            WorkspaceSection.Recorder -> CaptureTabContent(
                CaptureViewModel().apply { language = prefs.currentLanguage }
            )
            WorkspaceSection.TestRunner -> TestTabContent(
                TestRunnerViewModel().apply { language = prefs.currentLanguage }
            )
            WorkspaceSection.Plotter -> PlotterTabContent(
                PlotterViewModel().apply { language = prefs.currentLanguage }
            )
        }
        val tab = AppTab(
            id = id,
            title = fallbackTabTitle(section, titleIndex),
            titleIndex = titleIndex,
            content = content
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

        val activeTabId = host.activeTabBySection[section]
        val hasValidActiveTab = activeTabId != null &&
            activeTabId in host.tabIds &&
            tab(activeTabId)?.section == section
        if (hasValidActiveTab) return

        val existingTabId = host.tabIds.firstOrNull { tabId -> tab(tabId)?.section == section }
        if (existingTabId != null) {
            host.activeTabBySection[section] = existingTabId
        } else {
            // 功能域首次进入时懒创建默认页；已有会话仍优先恢复，不重复初始化资源。
            openTab(section, host.id)
        }
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

        // 关闭最后一个页面时自动补回基础终端，避免工作区进入没有可操作内容的死状态。
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

        // 只转移 tabId，AppTab 及其 ViewModel 仍保存在中央 tabs 表中。
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
        tabs.values.forEach { tab -> tab.content.applyTheme(prefs.isDark) }
    }

    fun switchLanguage() {
        prefs.switchLanguage()
        tabs.values.forEach { tab -> tab.content.applyLanguage(prefs.currentLanguage) }
    }

    fun toggleAlwaysOnTop() {
        prefs.isAlwaysOnTop = !prefs.isAlwaysOnTop
    }

    fun disposeAll() {
        // 退出前先保存仍然可读的页面状态，再关闭串口并清理全局监视器/租约。
        persistenceScope.cancel()
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
            // 新页面继承最近串口页面的配置；首次页面则使用磁盘中的启动快照。
            val source = tabs.values
                .mapNotNull { tab -> (tab.content as? SerialWorkspaceTabContent)?.viewModel }
                .lastOrNull()
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
        return settingsStore.save(persistedSettingsSnapshot())
    }

    private fun persistedSettingsSnapshot(): PersistedSettings {
        // 优先保存主窗口当前终端，若它不存在则使用任意仍存活的串口页面作为配置来源。
        val source = (tab(mainHost.activeTabBySection[WorkspaceSection.Basic])?.content as? SerialWorkspaceTabContent)?.viewModel
            ?: tabs.values.firstNotNullOfOrNull { (it.content as? SerialWorkspaceTabContent)?.viewModel }
        return PersistedSettings(
            appearance = prefs.toPersistedAppearance(),
            serial = source?.config?.toPersisted() ?: initialSettings.serial,
            preferences = source?.prefs?.toPersistedPreferences() ?: initialSettings.preferences,
            sendHistory = sendHistory.snapshot()
        )
    }

    private fun nextSectionTitleIndex(section: WorkspaceSection): Int {
        val count = (sectionCounts[section] ?: 0) + 1
        sectionCounts[section] = count
        return count
    }

    private fun fallbackTabTitle(section: WorkspaceSection, titleIndex: Int): String {
        val baseTitle = section.localizedTitle(prefs.currentLanguage)
        return if (titleIndex == 1) baseTitle else "$baseTitle $titleIndex"
    }

    private fun disposeTab(tabId: Int) {
        val tab = tabs.remove(tabId) ?: return
        tab.content.dispose()
    }

    private fun findHost(hostId: Int): WindowHostState? {
        if (hostId == MAIN_HOST_ID) return mainHost
        return floatingHosts.firstOrNull { it.id == hostId }
    }
}
