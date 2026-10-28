package com.yiaobang.kserial

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.i18n.AppStrings
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.i18n.ProvideAppStrings
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.theme.CupertinoTheme
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.ui.*
import com.yiaobang.kserial.utils.DesktopActions
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.LogExportFormat
import com.yiaobang.kserial.utils.LogExporter
import com.yiaobang.kserial.workspace.*
import kotlinx.coroutines.launch

// 桌面工作区的页面组合集中在此文件；串口和路由业务状态留在各自 ViewModel 中。
// 共享 UI 组件拆分在 ui/ 包：CommonControls（表单控件）、LogWidgets（日志组件）、PageLayout（页面骨架）。

/** 主窗口和浮动窗口共用的应用外壳：标题栏、侧栏、标签栏和活动页面。 */
@Composable
fun WorkspaceApp(
    workspace: WorkspaceState,
    host: WindowHostState,
    onClose: () -> Unit,
    onMinimize: () -> Unit
) {
    ProvideAppStrings(language = workspace.prefs.currentLanguage) {
        CupertinoTheme(isDark = workspace.prefs.isDark) {
            val colors = LocalAppColors.current
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.88f))
            ) {
                Column {
                    AppTopBar(
                        title = workspace.hostTitle(host),
                        isTop = workspace.prefs.isAlwaysOnTop,
                        onTop = { workspace.toggleAlwaysOnTop() },
                        onLanguage = { workspace.switchLanguage() },
                        onTheme = { workspace.switchTheme() },
                        onMinimize = onMinimize,
                        onClose = onClose
                    )

                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val compactSidebar = maxWidth < 1_040.dp
                        Row(modifier = Modifier.fillMaxSize()) {
                            WorkspaceSidebar(
                                selectedSection = host.selectedSection,
                                compact = compactSidebar,
                                onSelectSection = { workspace.selectSection(host.id, it) }
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                WorkspaceTabBar(
                                    workspace = workspace,
                                    host = host
                                )
                                WorkspaceContent(
                                    workspace = workspace,
                                    host = host,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSidebar(
    selectedSection: WorkspaceSection,
    compact: Boolean,
    onSelectSection: (WorkspaceSection) -> Unit
) {
    // 首次进入功能域时懒创建默认标签，之后恢复已有会话。
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .width(if (compact) 58.dp else 164.dp)
            .fillMaxHeight()
            .background(colors.topBar)
            .padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!compact) {
            Text(
                text = strings.workspace,
                color = colors.onTopBar.copy(alpha = 0.38f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Basic, strings),
            icon = Icons.Default.Terminal,
            compact = compact,
            selected = selectedSection == WorkspaceSection.Basic,
            onClick = { onSelectSection(WorkspaceSection.Basic) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Simulator, strings),
            icon = Icons.Default.Memory,
            compact = compact,
            selected = selectedSection == WorkspaceSection.Simulator,
            onClick = { onSelectSection(WorkspaceSection.Simulator) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Gateway, strings),
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            compact = compact,
            selected = selectedSection == WorkspaceSection.Gateway,
            onClick = { onSelectSection(WorkspaceSection.Gateway) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Recorder, strings),
            icon = Icons.Default.FiberManualRecord,
            compact = compact,
            selected = selectedSection == WorkspaceSection.Recorder,
            onClick = { onSelectSection(WorkspaceSection.Recorder) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.TestRunner, strings),
            icon = Icons.Default.CheckCircle,
            compact = compact,
            selected = selectedSection == WorkspaceSection.TestRunner,
            onClick = { onSelectSection(WorkspaceSection.TestRunner) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Plotter, strings),
            icon = Icons.AutoMirrored.Filled.ShowChart,
            compact = compact,
            selected = selectedSection == WorkspaceSection.Plotter,
            onClick = { onSelectSection(WorkspaceSection.Plotter) }
        )
    }
}

private fun sectionLabel(section: WorkspaceSection, strings: AppStrings): String = when (section) {
    WorkspaceSection.Basic -> strings.sectionBasic
    WorkspaceSection.Simulator -> strings.sectionSimulator
    WorkspaceSection.Gateway -> strings.sectionGateway
    WorkspaceSection.Recorder -> strings.sectionRecorder
    WorkspaceSection.TestRunner -> strings.sectionTestRunner
    WorkspaceSection.Plotter -> strings.sectionPlotter
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val item: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 0.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) colors.primary else colors.onTopBar.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
            if (!compact) {
                Text(
                    text = title,
                    color = if (selected) colors.onTopBar else colors.onTopBar.copy(alpha = 0.76f),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (compact) AppTooltip(title, item) else item()
}

@Composable
private fun WorkspaceTabBar(
    workspace: WorkspaceState,
    host: WindowHostState
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.surfaceMuted)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 每个功能域只显示自己的标签，切换功能时仍保留原来的活动标签。
            workspace.tabIdsFor(host).forEach { tabId ->
                val tab = workspace.tab(tabId) ?: return@forEach
                WorkspaceTabChip(
                    title = localizedTabTitle(tab, strings),
                    selected = host.activeTabBySection[host.selectedSection] == tabId,
                    floating = host.id != 0,
                    onSelect = { workspace.selectTab(host.id, tabId) },
                    onDetach = { workspace.detachTab(host.id, tabId) },
                    onMerge = { workspace.mergeTabToMain(host.id, tabId) },
                    onClose = { workspace.closeTab(host.id, tabId) }
                )
            }
            NewSectionTabButton(
                onClick = { workspace.openSelectedSectionTab(host.id) }
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.86f)))
    }
}

@Composable
private fun NewSectionTabButton(
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    AppTooltip(strings.actionNewTab) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Transparent)
                .border(1.dp, colors.border.copy(alpha = 0.82f), RoundedCornerShape(7.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = strings.actionNewTab,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurface.copy(alpha = 0.64f)
            )
        }
    }
}

@Composable
private fun WorkspaceTabChip(
    title: String,
    selected: Boolean,
    floating: Boolean,
    onSelect: () -> Unit,
    onDetach: () -> Unit,
    onMerge: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 130.dp, max = 220.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.surface else Color.Transparent)
            .border(1.dp, if (selected) colors.border else Color.Transparent, RoundedCornerShape(7.dp))
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.onSurface.copy(alpha = 0.22f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = if (selected) colors.onSurface else colors.onSurface.copy(alpha = 0.68f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        val moveDescription = if (floating) strings.actionMergeTab else strings.actionDetachTab
        AppTooltip(moveDescription) {
            IconButton(
                onClick = if (floating) onMerge else onDetach,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (floating) Icons.Default.Home else Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = moveDescription,
                    modifier = Modifier.size(15.dp),
                    tint = colors.onSurface.copy(alpha = 0.58f)
                )
            }
        }
        AppTooltip(strings.actionCloseTab) {
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.actionCloseTab,
                    modifier = Modifier.size(15.dp),
                    tint = colors.onSurface.copy(alpha = 0.58f)
                )
            }
        }
    }
}

@Composable
private fun WorkspaceContent(
    workspace: WorkspaceState,
    host: WindowHostState,
    modifier: Modifier
) {
    val tab = workspace.activeTab(host)
    Box(modifier = modifier.fillMaxSize()) {
        // tab id 变化时丢弃旧页面的 remember 状态，但 ViewModel 仍由工作区持有。
        key(tab?.id) {
            when (val content = tab?.content) {
                is BasicTabContent -> BasicSerialPage(viewModel = content.viewModel)
                is SimulatorTabContent -> SimulatorPage(viewModel = content.viewModel)
                is GatewayTabContent -> GatewayPage(viewModel = content.viewModel)
                is CaptureTabContent -> CapturePage(viewModel = content.viewModel)
                is TestTabContent -> TestRunnerPage(viewModel = content.viewModel)
                is PlotterTabContent -> PlotterPage(vm = content.viewModel)
                null -> EmptyFeaturePage(host.selectedSection)
            }
        }
    }
}

@Composable
private fun BasicSerialPage(
    viewModel: SerialViewModel
) {
    val prefs = viewModel.prefs
    val listState = rememberLazyListState()

    // 循环发送由界面状态驱动，端口断开或开关关闭时立即取消发送协程。
    LaunchedEffect(prefs.loopSendEnabled, prefs.loopIntervalText, viewModel.isPortOpen) {
        if (prefs.loopSendEnabled && viewModel.isPortOpen) {
            viewModel.startLoopSend()
        } else {
            viewModel.stopLoopSend()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 312.dp,
            mainContent = { contentModifier -> DataDisplayPanel(
                viewModel = viewModel,
                listState = listState,
                modifier = contentModifier
            ) },
            sideContent = { ControlPanel(vm = viewModel) }
        )
        AppStatusBar(viewModel)
    }
}

@Composable
private fun DataDisplayPanel(
    viewModel: SerialViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
    showSendComposer: Boolean = true
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val prefs = viewModel.prefs
    var confirmClearLogs by remember { mutableStateOf(false) }
    var logSplitRatio by remember { mutableStateOf(0.70f) }
    val coroutineScope = rememberCoroutineScope()
    // Ctrl+F 聚焦日志头部的搜索框，和常见日志查看器保持一致。
    val searchFocusRequester = remember { FocusRequester() }

    // 搜索和类型筛选只派生显示列表，不删除 ViewModel 中的完整会话日志。
    val visibleLogs by remember(viewModel) {
        derivedStateOf { viewModel.logs.filter { prefs.logFilter.accepts(it) } }
    }

    LaunchedEffect(visibleLogs.lastOrNull()?.id, prefs.logFilter.autoScroll) {
        if (prefs.logFilter.autoScroll && visibleLogs.isNotEmpty()) {
            listState.animateScrollToItem(visibleLogs.size)
        }
    }

    val isScrolledToBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 2
        }
    }

    val workbenchShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(workbenchShape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.84f), workbenchShape)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    searchFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
    ) {
        LogListHeader(
            totalCount = viewModel.logs.size,
            visibleCount = visibleLogs.size,
            query = prefs.logFilter.query,
            onCopyLogs = {
                DesktopActions.copyText(LogExporter.render(visibleLogs, LogExportFormat.TEXT))
                    .onSuccess { viewModel.addLog(strings.logsCopied, LogType.INFO) }
                    .onFailure { viewModel.addLog(strings.operationFailed(it.message.orEmpty()), LogType.INFO) }
            },
            onExportText = { exportSerialLogs(viewModel, visibleLogs, LogExportFormat.TEXT, strings) },
            onExportCsv = { exportSerialLogs(viewModel, visibleLogs, LogExportFormat.CSV, strings) },
            onClearLogs = { confirmClearLogs = true },
            onQueryChange = { prefs.logFilter.query = it },
            highlight = prefs.highlight,
            filter = prefs.logFilter,
            searchFocusRequester = searchFocusRequester
        )
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val totalHeightPx = constraints.maxHeight.toFloat()
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(if (showSendComposer) logSplitRatio else 1f)
                        .fillMaxWidth()
                        .background(colors.surfaceMuted.copy(alpha = 0.56f))
                ) {
                    if (visibleLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyLogState()
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            items(visibleLogs, key = { it.id }) { log ->
                                LogEntryRow(
                                    log = log,
                                    baseColor = when (log.type) {
                                        LogType.TX -> colors.primary
                                        LogType.RX -> colors.success
                                        else -> colors.onSurface.copy(0.4f)
                                    },
                                    highlight = prefs.highlight,
                                    extraHighlight = prefs.logFilter.query
                                )
                            }
                        }
                    }

                    // 快速滚回底部悬浮气泡
                    ScrollToBottomPill(
                        visible = !isScrolledToBottom && visibleLogs.isNotEmpty(),
                        count = visibleLogs.size,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp)
                    ) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(visibleLogs.size)
                        }
                    }
                }

                if (showSendComposer) {
                    PanelSplitter(
                        onDragDelta = { delta ->
                            if (totalHeightPx > 0f) {
                                logSplitRatio = (logSplitRatio + delta / totalHeightPx).coerceIn(0.20f, 0.85f)
                            }
                        },
                        onReset = { logSplitRatio = 0.70f }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f - logSplitRatio)
                            .fillMaxWidth()
                            .background(colors.surface)
                    ) {
                        // HEX 模式实时预检输入；真正发送前 ViewModel 仍会再次校验。
                        val hexResult = remember(prefs.hexSend, viewModel.inputText) {
                            if (prefs.hexSend && viewModel.inputText.isNotBlank()) {
                                HexInputParser.parse(viewModel.inputText)
                            } else {
                                null
                            }
                        }
                        BasicTextField(
                            value = viewModel.inputText,
                            onValueChange = {
                                viewModel.inputText = it
                                viewModel.sendHistory.resetNavigation()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 50.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    if (
                                        event.key == Key.Enter &&
                                        !event.isShiftPressed &&
                                        (event.isCtrlPressed || prefs.enterToSend)
                                    ) {
                                        viewModel.sendCurrentInput()
                                        return@onPreviewKeyEvent true
                                    }
                                    if (viewModel.inputText.contains('\n')) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionUp -> { viewModel.previousHistory(); true }
                                        Key.DirectionDown -> { viewModel.nextHistory(); true }
                                        else -> false
                                    }
                                },
                            textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                            cursorBrush = SolidColor(colors.primary)
                        )
                        if (viewModel.inputText.isBlank()) {
                            Text(
                                text = LocalAppStrings.current.sendInputHint,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 11.dp),
                                color = colors.onSurface.copy(alpha = 0.34f),
                                fontSize = 13.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(colors.surfaceMuted)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pendingBytes = viewModel.pendingSendByteCount()
                            Text(
                                text = if (hexResult?.isValid == false) strings.invalidHexInput else strings.byteCount(pendingBytes ?: 0),
                                modifier = Modifier.weight(1f),
                                color = if (hexResult?.isValid == false) colors.error else colors.onSurface.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.sendShortcutHint,
                                color = colors.onSurface.copy(alpha = 0.44f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            if (hexResult != null) {
                                TextButton(
                                    onClick = { if (hexResult.isValid) viewModel.inputText = hexResult.normalizedText },
                                    enabled = hexResult.isValid && viewModel.inputText.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(LocalAppStrings.current.btnFormatHex, fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { viewModel.sendCurrentInput() },
                                enabled = viewModel.canSend(viewModel.inputText),
                                modifier = Modifier.heightIn(min = 38.dp),
                                shape = RoundedCornerShape(7.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text(strings.btnSendData, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    ConfirmClearLogsDialog(
        visible = confirmClearLogs,
        onConfirm = { viewModel.clearLogs(); confirmClearLogs = false },
        onDismiss = { confirmClearLogs = false }
    )
}

@Composable
private fun SimulatorPage(viewModel: SerialViewModel) {
    // 模拟器只展示运行日志，配置加载和启停集中在右侧任务面板。
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 312.dp,
            mainContent = { contentModifier -> DataDisplayPanel(
                viewModel = viewModel,
                listState = listState,
                modifier = contentModifier,
                showSendComposer = false
            ) },
            sideContent = { SimulatorControlPanel(viewModel) }
        )
        AppStatusBar(viewModel)
    }
}

@Composable
private fun SimulatorControlPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        CardPanel(title = strings.panelConfig) {
            ActionButton(
                text = if (vm.isSimulating) strings.btnReloadConfig else strings.btnLoadConfig,
                color = if (vm.isSimulating) colors.success else colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                DesktopActions.selectConfigFile(strings.simulatorConfigDialogTitle)?.let(vm::loadSimulator)
            }
            if (vm.simulatorConfigName.isNotBlank()) {
                Text(
                    text = vm.simulatorConfigName,
                    color = colors.onSurface.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
                ConfigValidationSummary(vm.configIssues)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = strings.btnRun,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    enabled = vm.isSimulating && !vm.isConnectionActive && vm.config.selectedPort.isNotBlank()
                ) { vm.togglePort() }
                ActionButton(
                    text = strings.btnStop,
                    color = colors.surface,
                    modifier = Modifier.weight(1f),
                    enabled = vm.isConnectionActive
                ) { vm.togglePort() }
            }
        }
    }
}

private fun localizedTabTitle(tab: com.yiaobang.kserial.workspace.AppTab, strings: AppStrings): String {
    val selectedPort = tab.content.selectedPort
    if (!selectedPort.isNullOrBlank()) return selectedPort

    val sectionTitle = sectionLabel(tab.section, strings)
    return if (tab.titleIndex == 1) sectionTitle else "$sectionTitle ${tab.titleIndex}"
}

@Composable
private fun GatewayPage(viewModel: GatewayViewModel) {
    // 桥接主区只呈现实际发送日志，右侧只保留配置加载和整体启停。
    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 312.dp,
            mainContent = { contentModifier -> GatewayLogPanel(viewModel, modifier = contentModifier) },
            sideContent = { GatewayControlPanel(viewModel) }
        )
        GatewayStatusBar(viewModel)
    }
}

@Composable
private fun GatewayLogPanel(
    viewModel: GatewayViewModel,
    modifier: Modifier
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val listState = rememberLazyListState()
    var confirmClearLogs by remember { mutableStateOf(false) }
    val logBackground = colors.surfaceMuted.copy(alpha = 0.64f)
    // 导出和复制只使用当前可见集合，原始桥接日志仍完整保留。
    val visibleLogs by remember(viewModel) {
        derivedStateOf { viewModel.logs.filter { viewModel.logFilter.accepts(it) } }
    }

    LaunchedEffect(visibleLogs.lastOrNull()?.id, viewModel.logFilter.autoScroll) {
        if (viewModel.logFilter.autoScroll && visibleLogs.isNotEmpty()) {
            listState.animateScrollToItem(visibleLogs.size)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(logBackground)
            .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(7.dp)) {
            item {
                LogListHeader(
                    totalCount = viewModel.logs.size,
                    visibleCount = visibleLogs.size,
                    query = viewModel.logFilter.query,
                    onCopyLogs = {
                        DesktopActions.copyText(LogExporter.render(visibleLogs, LogExportFormat.TEXT))
                            .onSuccess { viewModel.reportInfo(strings.logsCopied) }
                            .onFailure { viewModel.reportInfo(strings.operationFailed(it.message.orEmpty())) }
                    },
                    onExportText = { exportGatewayLogs(viewModel, visibleLogs, LogExportFormat.TEXT, strings) },
                    onExportCsv = { exportGatewayLogs(viewModel, visibleLogs, LogExportFormat.CSV, strings) },
                    onClearLogs = { confirmClearLogs = true },
                    onQueryChange = { viewModel.logFilter.query = it },
                    highlight = viewModel.highlight,
                    filter = viewModel.logFilter
                )
                Spacer(Modifier.height(6.dp))
            }
            if (visibleLogs.isEmpty()) {
                item { EmptyLogState(strings.emptyGatewayLog) }
            } else {
                items(visibleLogs, key = { it.id }) { log ->
                    LogEntryRow(
                        log = log,
                        baseColor = when (log.type) {
                            LogType.TX -> colors.primary
                            LogType.RX -> colors.success
                            else -> colors.onSurface.copy(0.58f)
                        },
                        highlight = viewModel.highlight,
                        extraHighlight = viewModel.logFilter.query
                    )
                }
            }
        }
    }
    ConfirmClearLogsDialog(
        visible = confirmClearLogs,
        onConfirm = { viewModel.clearLogs(); confirmClearLogs = false },
        onDismiss = { confirmClearLogs = false }
    )
}

@Composable
private fun GatewayControlPanel(viewModel: GatewayViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        CardPanel(title = strings.panelConfig) {
            ActionButton(strings.btnLoadConfig, colors.surface, Modifier.fillMaxWidth()) {
                DesktopActions.selectConfigFile(strings.gatewayConfigDialogTitle)?.let(viewModel::loadConfig)
            }
            if (viewModel.loadedConfigName.isNotBlank()) {
                Text(
                    text = viewModel.loadedConfigName,
                    color = colors.onSurface.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
                ConfigValidationSummary(viewModel.configIssues)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    strings.btnRun,
                    colors.primary,
                    Modifier.weight(1f),
                    enabled = viewModel.endpoints.any { !it.isOpen && it.selectedPort.isNotBlank() }
                ) { viewModel.openAllEndpoints() }
                ActionButton(
                    strings.btnStop,
                    colors.surface,
                    Modifier.weight(1f),
                    enabled = viewModel.endpoints.any { it.isOpen }
                ) { viewModel.closeAllEndpoints() }
            }
        }
    }
}

@Composable
private fun GatewayStatusBar(viewModel: GatewayViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(BorderStroke(1.dp, colors.border.copy(0.3f)))
    ) {
        if (viewModel.endpoints.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = strings.noGatewayConfig,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = colors.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp
                )
            }
        } else {
            viewModel.endpoints.forEach { endpoint ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (endpoint.isOpen) colors.success.copy(alpha = 0.12f) else colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${endpoint.name}  TX ${endpoint.txCount}",
                        color = colors.onSurface.copy(alpha = 0.76f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun exportSerialLogs(
    viewModel: SerialViewModel,
    logs: List<LogEntry>,
    format: LogExportFormat,
    strings: AppStrings
) {
    DesktopActions.exportLogs(logs, format, strings.exportLogsDialogTitle)
        .onSuccess { file -> file?.let { viewModel.addLog(strings.logsExported(it.absolutePath), LogType.INFO) } }
        .onFailure { viewModel.addLog(strings.operationFailed(it.message.orEmpty()), LogType.INFO) }
}

private fun exportGatewayLogs(
    viewModel: GatewayViewModel,
    logs: List<LogEntry>,
    format: LogExportFormat,
    strings: AppStrings
) {
    DesktopActions.exportLogs(logs, format, strings.exportLogsDialogTitle)
        .onSuccess { file -> file?.let { viewModel.reportInfo(strings.logsExported(it.absolutePath)) } }
        .onFailure { viewModel.reportInfo(strings.operationFailed(it.message.orEmpty())) }
}

@Composable
private fun EmptyFeaturePage(section: WorkspaceSection) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(sectionLabel(section, strings), color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(strings.emptyCreatePage, color = colors.onSurface.copy(alpha = 0.58f), fontSize = 13.sp)
    }
}
