package com.yiaobang.kserial

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.GatewayEndpoint
import com.yiaobang.kserial.feature.GatewayMode
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.i18n.AppStrings
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.i18n.ProvideAppStrings
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogEntry
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.theme.CupertinoTheme
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.ui.*
import com.yiaobang.kserial.utils.HexInputParser
import com.yiaobang.kserial.utils.DesktopActions
import com.yiaobang.kserial.utils.LogExportFormat
import com.yiaobang.kserial.utils.LogExporter
import com.yiaobang.kserial.workspace.WindowHostState
import com.yiaobang.kserial.workspace.WorkspaceSection
import com.yiaobang.kserial.workspace.WorkspaceState

// 桌面工作区的页面组合集中在此文件；串口和路由业务状态留在各自 ViewModel 中。

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
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
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

                    Row(modifier = Modifier.weight(1f)) {
                        WorkspaceSidebar(
                            selectedSection = host.selectedSection,
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

@Composable
private fun WorkspaceSidebar(
    selectedSection: WorkspaceSection,
    onSelectSection: (WorkspaceSection) -> Unit
) {
    // 切换功能域不会隐式新建页面；没有页面时由内容区提示用户点击“+”。
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .width(188.dp)
            .fillMaxHeight()
            .background(colors.surface.copy(alpha = 0.96f))
            .border(width = 1.dp, color = colors.border.copy(alpha = 0.38f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = strings.workspace,
            color = colors.onSurface.copy(alpha = 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Basic, strings),
            icon = Icons.Default.Terminal,
            selected = selectedSection == WorkspaceSection.Basic,
            onClick = { onSelectSection(WorkspaceSection.Basic) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Simulator, strings),
            icon = Icons.Default.Memory,
            selected = selectedSection == WorkspaceSection.Simulator,
            onClick = { onSelectSection(WorkspaceSection.Simulator) }
        )
        SidebarItem(
            title = sectionLabel(WorkspaceSection.Gateway, strings),
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            selected = selectedSection == WorkspaceSection.Gateway,
            onClick = { onSelectSection(WorkspaceSection.Gateway) }
        )
    }
}

private fun sectionLabel(section: WorkspaceSection, strings: AppStrings): String = when (section) {
    WorkspaceSection.Basic -> strings.sectionBasic
    WorkspaceSection.Simulator -> strings.sectionSimulator
    WorkspaceSection.Gateway -> strings.sectionGateway
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = if (selected) colors.primary else colors.onSurface,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorkspaceTabBar(
    workspace: WorkspaceState,
    host: WindowHostState
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.topBar)
            .border(width = 1.dp, color = colors.border.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
}

@Composable
private fun NewSectionTabButton(
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.background.copy(alpha = 0.55f))
            .border(1.dp, colors.border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = strings.actionNewTab,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurface.copy(alpha = 0.76f)
        )
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.surface else colors.background.copy(alpha = 0.55f))
            .border(1.dp, if (selected) colors.primary.copy(alpha = 0.42f) else colors.border.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = colors.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        IconButton(
            onClick = if (floating) onMerge else onDetach,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = if (floating) Icons.Default.Home else Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = if (floating) strings.actionMergeTab else strings.actionDetachTab,
                modifier = Modifier.size(15.dp),
                tint = colors.onSurface.copy(alpha = 0.58f)
            )
        }
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
            when (tab?.section) {
                WorkspaceSection.Basic -> {
                    tab.serialViewModel?.let { BasicSerialPage(viewModel = it) }
                        ?: EmptyFeaturePage(host.selectedSection)
                }
                WorkspaceSection.Simulator -> {
                    tab.serialViewModel?.let { SimulatorPage(viewModel = it) }
                        ?: EmptyFeaturePage(host.selectedSection)
                }
                WorkspaceSection.Gateway -> {
                    tab.gatewayViewModel?.let { GatewayPage(viewModel = it) }
                        ?: EmptyFeaturePage(host.selectedSection)
                }
                null -> EmptyFeaturePage(host.selectedSection)
            }
        }
    }
}

@Composable
private fun ScrollableSidePanel(
    width: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // 控制项可能超过窗口高度，始终显示滚动条提示下方还有更多设置。
    val scrollState = rememberScrollState()
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp)
                .verticalScroll(scrollState),
            content = content
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(8.dp),
            style = ScrollbarStyle(
                minimalHeight = 36.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 180,
                unhoverColor = colors.border.copy(alpha = 0.62f),
                hoverColor = colors.primary.copy(alpha = 0.72f)
            )
        )
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
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            DataDisplayPanel(
                viewModel = viewModel,
                listState = listState,
                modifier = Modifier.weight(1f)
            )
            ScrollableSidePanel(width = 332.dp) {
                ControlPanel(vm = viewModel)
            }
        }
        AppStatusBar(viewModel)
    }
}

@Composable
private fun DataDisplayPanel(
    viewModel: SerialViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val prefs = viewModel.prefs
    var confirmClearLogs by remember { mutableStateOf(false) }
    val logBackground = if (prefs.isDark) Color(0xFF141414) else Color(0xFFF9F9F9)
    // 搜索和类型筛选只派生显示列表，不删除 ViewModel 中的完整会话日志。
    val visibleLogs by remember(viewModel) {
        derivedStateOf { viewModel.logs.filter { prefs.logFilter.accepts(it) } }
    }

    LaunchedEffect(visibleLogs.lastOrNull()?.id, prefs.logFilter.autoScroll) {
        if (prefs.logFilter.autoScroll && visibleLogs.isNotEmpty()) {
            listState.animateScrollToItem(visibleLogs.size)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(logBackground)
                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(7.dp)) {
                item {
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
                        onClearLogs = { confirmClearLogs = true }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (visibleLogs.isEmpty()) {
                    item { EmptyLogState() }
                } else {
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
        }

        Box(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface)
                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
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
                    .padding(8.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || viewModel.inputText.contains('\n')) return@onPreviewKeyEvent false
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
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = colors.onSurface.copy(alpha = 0.34f),
                    fontSize = 13.sp
                )
            }
            if (hexResult != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(colors.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hexResult.error ?: "${hexResult.byteCount} bytes",
                        color = if (hexResult.isValid) colors.onSurface.copy(alpha = 0.58f) else colors.error,
                        fontSize = 11.sp
                    )
                    TextButton(
                        onClick = { if (hexResult.isValid) viewModel.inputText = hexResult.normalizedText },
                        enabled = hexResult.isValid && viewModel.inputText.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(LocalAppStrings.current.btnFormatHex, fontSize = 11.sp)
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
    // 模拟器复用终端数据区，右侧换成配置、规则状态和自动回复控制。
    val prefs = viewModel.prefs
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            DataDisplayPanel(
                viewModel = viewModel,
                listState = listState,
                modifier = Modifier.weight(1f)
            )
            ScrollableSidePanel(width = 332.dp) {
                SimulatorControlPanel(viewModel)
            }
        }
        AppStatusBar(viewModel)
    }

    LaunchedEffect(prefs.loopSendEnabled, prefs.loopIntervalText, viewModel.isPortOpen) {
        if (prefs.loopSendEnabled && viewModel.isPortOpen) viewModel.startLoopSend()
        else viewModel.stopLoopSend()
    }
}

@Composable
private fun SimulatorControlPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val config = vm.config
    Column(
        modifier = Modifier.width(320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
            SummaryLine(strings.labelSerialPort, config.selectedPort.ifBlank { strings.notConfigured })
            SummaryLine(strings.labelBaudRate, config.baudRate.toString())
            SummaryLine(strings.labelDataBits, config.dataBits.toString())
        }
        CardPanel(title = strings.panelSimulator) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (vm.isPortOpen) strings.btnClosePort else strings.btnOpenPort,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    enabled = vm.isPortOpen || config.selectedPort.isNotBlank()
                ) { vm.togglePort() }
                ActionButton(
                    strings.btnClearLog,
                    colors.surface,
                    Modifier.weight(1f),
                    enabled = vm.logs.isNotEmpty()
                ) { vm.clearLogs() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.padding(top = 4.dp)) {
                SmallStatusDot(vm.isPortOpen, strings.labelSerialPort)
                SmallStatusDot(vm.isSimulating, strings.labelRule)
            }
        }
        CardPanel(title = strings.panelActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    strings.btnSendData,
                    colors.success,
                    Modifier.weight(1f),
                    enabled = vm.canSend(vm.inputText)
                ) { vm.sendData(vm.inputText) }
                ActionButton(
                    strings.btnClearSend,
                    colors.surface,
                    Modifier.weight(1f),
                    enabled = vm.inputText.isNotEmpty()
                ) { vm.inputText = "" }
            }
        }
        CardPanel(title = strings.panelQuickSend) {
            QuickSendPanel(vm)
        }
        ExpandableCardPanel(title = strings.panelSendHistory) {
            SendHistoryPanel(vm)
        }
        ExpandableCardPanel(title = strings.panelProtocolTools) {
            ProtocolToolsPanel(vm)
        }
        ExpandableCardPanel(title = strings.panelOptions) {
            OptionsGrid(vm)
        }
        ExpandableCardPanel(title = strings.panelHighlight) {
            HighlightEditor(vm.prefs.highlight)
        }
        ExpandableCardPanel(title = strings.panelLog) {
            LogFilterEditor(vm.prefs.logFilter)
        }
    }
}

private fun localizedTabTitle(tab: com.yiaobang.kserial.workspace.AppTab, strings: AppStrings): String {
    val selectedPort = tab.serialViewModel?.config?.selectedPort
    if (!selectedPort.isNullOrBlank()) return selectedPort

    val sectionTitle = sectionLabel(tab.section, strings)
    return if (tab.titleIndex == 1) sectionTitle else "$sectionTitle ${tab.titleIndex}"
}

@Composable
private fun GatewayPage(viewModel: GatewayViewModel) {
    // 桥接需同时展示转发日志与多个端点，因此使用比普通终端更宽的控制侧栏。
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            GatewayLogPanel(viewModel, modifier = Modifier.weight(1f))
            ScrollableSidePanel(width = 372.dp) {
                GatewayControlPanel(viewModel)
            }
        }
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
    val logBackground = if (colors.surface.luminance() > 0.5f) Color(0xFFF9F9F9) else Color(0xFF141414)
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
                    onClearLogs = { confirmClearLogs = true }
                )
                Spacer(Modifier.height(6.dp))
            }
            if (visibleLogs.isEmpty()) {
                item { EmptyLogState() }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
        CardPanel(title = strings.panelRoute) {
            if (viewModel.endpoints.isEmpty()) {
                Text(
                    text = strings.loadGatewayConfigHint,
                    color = colors.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp
                )
            } else if (viewModel.routes.isNotEmpty()) {
                viewModel.routes.forEach { route ->
                    RouteLine(
                        name = route.name,
                        content = "${route.sourceIds.joinToString()} -> ${route.targetIds.joinToString()}",
                        enabled = route.enabled
                    )
                }
            } else {
                val sourceName = viewModel.endpoints.getOrNull(viewModel.sourceIndex)?.name ?: ""
                val targetName = viewModel.endpoints.getOrNull(viewModel.targetIndex)?.name ?: ""
                val modeLabels = GatewayMode.entries.map { gatewayModeLabel(it, strings) }
                SelectRow(strings.labelMode, gatewayModeLabel(viewModel.mode, strings), modeLabels) { label ->
                    viewModel.mode = GatewayMode.entries.first { gatewayModeLabel(it, strings) == label }
                }
                SelectRow(strings.labelSource, sourceName, viewModel.endpoints.map { it.name }) { name ->
                    viewModel.sourceIndex = viewModel.endpoints.indexOfFirst { it.name == name }.coerceAtLeast(0)
                }
                SelectRow(strings.labelTarget, targetName, viewModel.endpoints.map { it.name }) { name ->
                    viewModel.targetIndex = viewModel.endpoints.indexOfFirst { it.name == name }.coerceAtLeast(0)
                }
            }
            OptionCheck(viewModel.hexDisplay, strings.optHexDisplay) { viewModel.hexDisplay = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    strings.btnClearLog,
                    colors.surface,
                    Modifier.weight(1f),
                    enabled = viewModel.logs.isNotEmpty()
                ) { viewModel.clearLogs() }
                ActionButton(
                    strings.btnClearCounters,
                    colors.surface,
                    Modifier.weight(1f),
                    enabled = viewModel.endpoints.any { it.txCount > 0 || it.rxCount > 0 }
                ) { viewModel.clearCounters() }
            }
        }
        CardPanel(title = strings.panelHighlight) {
            HighlightEditor(viewModel.highlight)
        }
        CardPanel(title = strings.panelLog) {
            LogFilterEditor(viewModel.logFilter)
        }
        viewModel.endpoints.forEach { endpoint ->
            GatewayEndpointCard(viewModel, endpoint)
        }
    }
}

private fun gatewayModeLabel(mode: GatewayMode, strings: AppStrings): String = when (mode) {
    GatewayMode.OneToOne -> strings.gatewayModeOneToOne
    GatewayMode.OneToMany -> strings.gatewayModeOneToMany
    GatewayMode.ManyToMany -> strings.gatewayModeManyToMany
}

@Composable
private fun GatewayEndpointCard(
    viewModel: GatewayViewModel,
    endpoint: GatewayEndpoint
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    CardPanel(title = strings.panelEndpoint(endpoint.name)) {
        SummaryLine(strings.labelSerialPort, endpoint.selectedPort.ifBlank { strings.notConfigured })
        SummaryLine(strings.labelBaudRate, endpoint.baudRate.toString())
        SummaryLine(strings.labelDataBits, endpoint.dataBits.toString())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = if (endpoint.isOpen) strings.btnClose else strings.btnOpen,
                color = if (endpoint.isOpen) colors.success else colors.primary,
                modifier = Modifier.weight(1f),
                enabled = endpoint.isOpen || endpoint.selectedPort.isNotBlank()
            ) { viewModel.toggleEndpoint(endpoint) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                SmallStatusDot(endpoint.isOpen, "${endpoint.txCount}/${endpoint.rxCount}")
            }
        }
    }
}

@Composable
private fun RouteLine(
    name: String,
    content: String,
    enabled: Boolean
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(colors.background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = content,
            color = colors.onSurface.copy(alpha = 0.62f),
            fontSize = 12.sp
        )
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
                        text = "${endpoint.name}  TX ${endpoint.txCount} / RX ${endpoint.rxCount}",
                        color = colors.onSurface.copy(alpha = 0.76f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LogListHeader(
    totalCount: Int,
    visibleCount: Int,
    query: String,
    onCopyLogs: () -> Unit,
    onExportText: () -> Unit,
    onExportCsv: () -> Unit,
    onClearLogs: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.logCount(visibleCount, totalCount),
            modifier = Modifier
                .background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.onSurface.copy(alpha = 0.52f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (query.isNotBlank()) {
            Text(
                text = strings.searchPrefix(query),
                color = colors.primary.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        LogHeaderAction(Icons.Default.ContentCopy, strings.btnCopyLogs, onCopyLogs)
        LogHeaderAction(Icons.Default.Description, strings.btnExportText, onExportText)
        LogHeaderAction(Icons.Default.TableChart, strings.btnExportCsv, onExportCsv)
        LogHeaderAction(Icons.Default.DeleteSweep, strings.actionClearLogs, onClearLogs)
    }
}

@Composable
private fun LogHeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(17.dp),
            tint = LocalAppColors.current.onSurface.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun LogEntryRow(
    log: LogEntry,
    baseColor: Color,
    highlight: KeywordHighlightConfig,
    extraHighlight: String = ""
) {
    val strings = LocalAppStrings.current
    val rowText = "[${log.time}] ${log.type.name.padEnd(4)} ${log.content}"
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(strings.actionCopyRow) { DesktopActions.copyText(rowText) },
                ContextMenuItem(strings.actionCopyContent) { DesktopActions.copyText(log.content) }
            )
        }
    ) {
        LogLine(rowText, baseColor, highlight, extraHighlight)
    }
}

@Composable
private fun ConfirmClearLogsDialog(visible: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmClearLogsTitle) },
        text = { Text(strings.confirmClearLogsMessage) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.btnConfirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.btnCancel) } }
    )
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
private fun EmptyLogState() {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DataObject,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = colors.onSurface.copy(alpha = 0.24f)
        )
        Text(
            text = strings.emptyLog,
            color = colors.onSurface.copy(alpha = 0.46f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LogLine(
    text: String,
    baseColor: Color,
    highlight: KeywordHighlightConfig,
    extraHighlight: String = ""
) {
    val keywords = remember(highlight.keywordsText) { highlight.keywords }
    val activeKeywords = remember(keywords, extraHighlight) {
        buildList {
            addAll(keywords)
            extraHighlight.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
    Text(
        text = if (highlight.enabled || extraHighlight.isNotBlank()) {
            buildHighlightedLogText(
                text = text,
                keywords = activeKeywords,
                ignoreCase = highlight.ignoreCase
            )
        } else {
            AnnotatedString(text)
        },
        color = baseColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
}

private fun buildHighlightedLogText(
    text: String,
    keywords: List<String>,
    ignoreCase: Boolean
): AnnotatedString {
    // 合并关键词和搜索词命中范围后统一着色，避免重叠区间重复拼接文本。
    val ranges = findKeywordRanges(text, keywords, ignoreCase)
    if (ranges.isEmpty()) return AnnotatedString(text)

    val style = SpanStyle(
        color = Color.Black,
        background = Color(0xFFFFE58F),
        fontWeight = FontWeight.Bold
    )
    return AnnotatedString(
        text = text,
        spanStyles = ranges.map { range ->
            AnnotatedString.Range(style, range.first, range.last + 1)
        }
    )
}

private fun findKeywordRanges(
    text: String,
    keywords: List<String>,
    ignoreCase: Boolean
): List<IntRange> {
    // 每次命中后至少推进一个字符，允许重叠匹配并避免空关键词造成死循环。
    val ranges = mutableListOf<IntRange>()
    keywords.forEach { keyword ->
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(keyword, startIndex = start, ignoreCase = ignoreCase)
            if (index < 0) break
            ranges += index until index + keyword.length
            start = index + keyword.length
        }
    }
    if (ranges.isEmpty()) return emptyList()

    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    sorted.forEach { range ->
        val last = merged.lastOrNull()
        if (last == null || range.first > last.last + 1) {
            merged += range
        } else if (range.last > last.last) {
            merged[merged.lastIndex] = last.first..range.last
        }
    }
    return merged
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

@Composable
private fun SummaryLine(
    label: String,
    value: String
) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(90.dp), fontSize = 13.sp, color = colors.onSurface)
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.border.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = 13.sp,
            color = colors.onSurface.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun SelectRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(90.dp), fontSize = 13.sp, color = colors.onSurface)
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                    .clickable(enabled = options.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(value.ifBlank { strings.notSelected }, fontSize = 13.sp, color = colors.onSurface)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface).border(1.dp, colors.border)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp, color = colors.onSurface) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCheck(
    checked: Boolean,
    label: String,
    onChecked: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, fontSize = 12.sp, color = LocalAppColors.current.onSurface)
    }
}

@Composable
private fun SmallStatusDot(
    active: Boolean,
    label: String
) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.size(15.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) colors.success else colors.onSurface.copy(alpha = 0.30f))
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.onSurface.copy(alpha = 0.72f)
        )
    }
}
