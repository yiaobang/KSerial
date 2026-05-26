package com.yiaobang.kserial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.GatewayEndpoint
import com.yiaobang.kserial.feature.GatewayMode
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.model.KeywordHighlightConfig
import com.yiaobang.kserial.model.LogType
import com.yiaobang.kserial.theme.CupertinoTheme
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.ui.ActionButton
import com.yiaobang.kserial.ui.AppStatusBar
import com.yiaobang.kserial.ui.AppTopBar
import com.yiaobang.kserial.ui.CardPanel
import com.yiaobang.kserial.ui.ControlPanel
import com.yiaobang.kserial.ui.HighlightEditor
import com.yiaobang.kserial.ui.LogFilterEditor
import com.yiaobang.kserial.workspace.WindowHostState
import com.yiaobang.kserial.workspace.WorkspaceSection
import com.yiaobang.kserial.workspace.WorkspaceState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun WorkspaceApp(
    workspace: WorkspaceState,
    host: WindowHostState,
    onClose: () -> Unit,
    onMinimize: () -> Unit
) {
    CupertinoTheme(isDark = workspace.prefs.isDark) {
        val colors = LocalAppColors.current
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Column {
                AppTopBar(
                    title = workspace.hostTitle(host),
                    isTop = workspace.prefs.isAlwaysOnTop,
                    onTop = { workspace.prefs.isAlwaysOnTop = !workspace.prefs.isAlwaysOnTop },
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

@Composable
private fun WorkspaceSidebar(
    selectedSection: WorkspaceSection,
    onSelectSection: (WorkspaceSection) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .width(164.dp)
            .fillMaxHeight()
            .background(colors.surface.copy(alpha = 0.96f))
            .border(width = 1.dp, color = colors.border.copy(alpha = 0.38f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "WORKSPACE",
            color = colors.onSurface.copy(alpha = 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
        SidebarItem(
            title = WorkspaceSection.Basic.label,
            icon = Icons.Default.Power,
            selected = selectedSection == WorkspaceSection.Basic,
            onClick = { onSelectSection(WorkspaceSection.Basic) }
        )
        SidebarItem(
            title = WorkspaceSection.Simulator.label,
            icon = Icons.Default.Reply,
            selected = selectedSection == WorkspaceSection.Simulator,
            onClick = { onSelectSection(WorkspaceSection.Simulator) }
        )
        SidebarItem(
            title = WorkspaceSection.Gateway.label,
            icon = Icons.Default.Share,
            selected = selectedSection == WorkspaceSection.Gateway,
            onClick = { onSelectSection(WorkspaceSection.Gateway) }
        )
    }
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
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = if (selected) colors.primary else colors.onSurface,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun WorkspaceTabBar(
    workspace: WorkspaceState,
    host: WindowHostState
) {
    val colors = LocalAppColors.current
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
        workspace.tabIdsFor(host).forEach { tabId ->
            val tab = workspace.tab(tabId) ?: return@forEach
            WorkspaceTabChip(
                title = workspace.tabTitle(tab),
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
            contentDescription = null,
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
                imageVector = if (floating) Icons.Default.Home else Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.onSurface.copy(alpha = 0.58f)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
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
        when (tab?.section) {
            WorkspaceSection.Basic -> {
                val viewModel = tab.serialViewModel ?: return
                BasicSerialPage(viewModel = viewModel)
            }
            WorkspaceSection.Simulator -> {
                val viewModel = tab.serialViewModel ?: return
                SimulatorPage(viewModel = viewModel)
            }
            WorkspaceSection.Gateway -> {
                val viewModel = tab.gatewayViewModel ?: return
                GatewayPage(viewModel = viewModel)
            }
            null -> EmptyFeaturePage(host.selectedSection)
        }
    }
}

@Composable
private fun BasicSerialPage(
    viewModel: SerialViewModel
) {
    val prefs = viewModel.prefs
    val listState = rememberLazyListState()

    LaunchedEffect(prefs.loopSendEnabled, viewModel.isPortOpen) {
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
    val prefs = viewModel.prefs
    val logBackground = if (prefs.isDark) Color(0xFF141414) else Color(0xFFF9F9F9)
    val visibleLogs = remember(
        viewModel.logs.size,
        prefs.logFilter.query,
        prefs.logFilter.onlyMatches,
        prefs.logFilter.showTx,
        prefs.logFilter.showRx,
        prefs.logFilter.showInfo
    ) {
        viewModel.logs.filter { prefs.logFilter.accepts(it) }
    }

    LaunchedEffect(visibleLogs.size, prefs.logFilter.autoScroll) {
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
                        query = prefs.logFilter.query
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(visibleLogs) { log ->
                    LogLine(
                        text = "[${log.time}] ${if (log.type == LogType.TX) "->" else "<-"} ${log.content}",
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

        Box(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface)
                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
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

@Composable
private fun SimulatorPage(viewModel: SerialViewModel) {
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SimulatorControlPanel(viewModel)
            }
        }
        AppStatusBar(viewModel)
    }

    LaunchedEffect(prefs.loopSendEnabled, viewModel.isPortOpen) {
        if (prefs.loopSendEnabled && viewModel.isPortOpen) viewModel.startLoopSend()
        else viewModel.stopLoopSend()
    }
}

@Composable
private fun SimulatorControlPanel(vm: SerialViewModel) {
    val colors = LocalAppColors.current
    val config = vm.config
    val prefs = vm.prefs
    Column(
        modifier = Modifier.width(310.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CardPanel(title = "CONFIG") {
            ActionButton(
                text = if (vm.isSimulating) "重新加载配置" else "加载配置",
                color = if (vm.isSimulating) colors.success else colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                val dialog = FileDialog(null as Frame?, "选择自动回复配置文件", FileDialog.LOAD).apply {
                    file = "*.conf"
                    isVisible = true
                }
                dialog.file?.let { vm.loadSimulator(File(dialog.directory, it)) }
            }
            if (vm.simulatorConfigName.isNotBlank()) {
                Text(
                    text = vm.simulatorConfigName,
                    color = colors.onSurface.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
            }
            SummaryLine("串口", config.selectedPort.ifBlank { "未配置" })
            SummaryLine("波特率", config.baudRate.toString())
            SummaryLine("数据位", config.dataBits.toString())
        }
        CardPanel(title = "SIMULATOR") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (vm.isPortOpen) "关闭串口" else "打开串口",
                    color = colors.primary,
                    modifier = Modifier.weight(1f)
                ) { vm.togglePort() }
                ActionButton("清除日志", colors.surface, Modifier.weight(1f)) { vm.clearLogs() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.padding(top = 4.dp)) {
                SmallStatusDot(vm.isPortOpen, "串口")
                SmallStatusDot(vm.isSimulating, "规则")
            }
        }
        CardPanel(title = "OPTIONS") {
            OptionCheck(prefs.showRealTime, "实时显示") { prefs.showRealTime = it }
            OptionCheck(prefs.hexDisplay, "HEX 显示") { prefs.hexDisplay = it }
            OptionCheck(prefs.hexSend, "HEX 发送") { prefs.hexSend = it }
        }
        CardPanel(title = "HIGHLIGHT") {
            HighlightEditor(prefs.highlight)
        }
    }
}

@Composable
private fun GatewayPage(viewModel: GatewayViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            GatewayLogPanel(viewModel, modifier = Modifier.weight(1f))
            Column(modifier = Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
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
    val listState = rememberLazyListState()
    val logBackground = if (colors.surface.luminance() > 0.5f) Color(0xFFF9F9F9) else Color(0xFF141414)
    val visibleLogs = remember(
        viewModel.logs.size,
        viewModel.logFilter.query,
        viewModel.logFilter.onlyMatches,
        viewModel.logFilter.showTx,
        viewModel.logFilter.showRx,
        viewModel.logFilter.showInfo
    ) {
        viewModel.logs.filter { viewModel.logFilter.accepts(it) }
    }

    LaunchedEffect(visibleLogs.size, viewModel.logFilter.autoScroll) {
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
                    query = viewModel.logFilter.query
                )
                Spacer(Modifier.height(6.dp))
            }
            items(visibleLogs) { log ->
                LogLine(
                    text = "[${log.time}] ${if (log.type == LogType.TX) "->" else "<-"} ${log.content}",
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

@Composable
private fun GatewayControlPanel(viewModel: GatewayViewModel) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardPanel(title = "CONFIG") {
            ActionButton("加载配置", colors.surface, Modifier.fillMaxWidth()) {
                val dialog = FileDialog(null as Frame?, "选择网关配置文件", FileDialog.LOAD).apply {
                    file = "*.conf"
                    isVisible = true
                }
                dialog.file?.let { viewModel.loadConfig(File(dialog.directory, it)) }
            }
            if (viewModel.loadedConfigName.isNotBlank()) {
                Text(
                    text = viewModel.loadedConfigName,
                    color = colors.onSurface.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
            }
        }
        CardPanel(title = "ROUTE") {
            if (viewModel.endpoints.isEmpty()) {
                Text(
                    text = "加载 conf 后显示端点和路由",
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
                SelectRow("模式", viewModel.mode.label, GatewayMode.entries.map { it.label }) { label ->
                    viewModel.mode = GatewayMode.entries.first { it.label == label }
                }
                SelectRow("来源", sourceName, viewModel.endpoints.map { it.name }) { name ->
                    viewModel.sourceIndex = viewModel.endpoints.indexOfFirst { it.name == name }.coerceAtLeast(0)
                }
                SelectRow("目标", targetName, viewModel.endpoints.map { it.name }) { name ->
                    viewModel.targetIndex = viewModel.endpoints.indexOfFirst { it.name == name }.coerceAtLeast(0)
                }
            }
            OptionCheck(viewModel.hexDisplay, "HEX 显示") { viewModel.hexDisplay = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("清除日志", colors.surface, Modifier.weight(1f)) { viewModel.clearLogs() }
                ActionButton("清零计数", colors.surface, Modifier.weight(1f)) { viewModel.clearCounters() }
            }
        }
        CardPanel(title = "HIGHLIGHT") {
            HighlightEditor(viewModel.highlight)
        }
        CardPanel(title = "LOG") {
            LogFilterEditor(viewModel.logFilter)
        }
        viewModel.endpoints.forEach { endpoint ->
            GatewayEndpointCard(viewModel, endpoint)
        }
    }
}

@Composable
private fun GatewayEndpointCard(
    viewModel: GatewayViewModel,
    endpoint: GatewayEndpoint
) {
    val colors = LocalAppColors.current
    CardPanel(title = "ENDPOINT ${endpoint.name}") {
        SummaryLine("串口", endpoint.selectedPort.ifBlank { "未配置" })
        SummaryLine("波特率", endpoint.baudRate.toString())
        SummaryLine("数据位", endpoint.dataBits.toString())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = if (endpoint.isOpen) "关闭" else "打开",
                color = if (endpoint.isOpen) colors.success else colors.primary,
                modifier = Modifier.weight(1f)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(BorderStroke(1.dp, colors.border.copy(0.3f)))
    ) {
        if (viewModel.endpoints.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "未加载网关配置",
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
    query: String
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "日志 $visibleCount / $totalCount",
            modifier = Modifier
                .background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.onSurface.copy(alpha = 0.52f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (query.isNotBlank()) {
            Text(
                text = "搜索: $query",
                color = colors.primary.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false)
            )
        }
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
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(section.label, color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("点击上方 + 创建一个页面", color = colors.onSurface.copy(alpha = 0.58f), fontSize = 13.sp)
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
                Text(value.ifBlank { "未选择" }, fontSize = 13.sp, color = colors.onSurface)
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
                    .background(if (active) colors.success else colors.error)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.onSurface.copy(alpha = 0.72f)
        )
    }
}
