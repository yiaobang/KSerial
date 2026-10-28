package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.feature.TestRunnerViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.TestMatchMode
import com.yiaobang.kserial.model.TestSequenceRunner
import com.yiaobang.kserial.theme.LocalAppColors
import com.yiaobang.kserial.utils.DesktopActions

/**
 * 测试序列页：左侧是步骤结果表，右侧是配置、运行和汇总。
 * 未运行时也展示步骤清单，方便先看清序列内容再执行。
 */
@Composable
fun TestRunnerPage(viewModel: TestRunnerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        AdaptivePageBody(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            sideWidth = 312.dp,
            mainContent = { modifier -> TestResultTable(viewModel, modifier) },
            sideContent = { TestRunnerControlPanel(viewModel) }
        )
    }
}

@Composable
private fun TestRunnerControlPanel(vm: TestRunnerViewModel) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        CardPanel(title = strings.panelConfig) {
            ActionButton(
                text = if (vm.loadedName.isBlank()) strings.testLoad else strings.btnLoadConfig,
                color = colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                DesktopActions.selectConfigFile(strings.testLoad)?.let(vm::loadConfig)
            }
            if (vm.loadedName.isNotBlank()) {
                Text(vm.loadedName, color = colors.onSurface.copy(alpha = 0.68f), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = strings.testRun,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    enabled = vm.canRun
                ) { vm.runSequence() }
                ActionButton(
                    text = strings.testStop,
                    color = colors.surface,
                    modifier = Modifier.weight(1f),
                    enabled = vm.running
                ) { vm.stop() }
            }
            if (vm.summary.isNotBlank()) {
                Text(
                    text = vm.summary,
                    color = if (vm.lastReport?.allPassed == true) colors.success else colors.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (vm.results.isNotEmpty()) {
                ActionButton(
                    text = strings.plotterClear,
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth()
                ) { vm.clearResults() }
            }
        }
    }
}

@Composable
private fun TestResultTable(vm: TestRunnerViewModel, modifier: Modifier) {
    val strings = LocalAppStrings.current
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    val steps = vm.sequence?.steps.orEmpty()
    val resultsByIndex = vm.results.associateBy { it.index }

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.84f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (steps.isEmpty()) strings.testEmpty else "${steps.size}",
                modifier = Modifier
                    .background(colors.surfaceMuted, RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = colors.onSurface.copy(alpha = 0.68f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (vm.results.isNotEmpty()) {
                TextButton(
                    onClick = { DesktopActions.copyText(vm.reportText()) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(strings.btnCopyLogs, fontSize = 11.sp)
                }
            }
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted.copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            HeaderCell(strings.testColStep, Modifier.weight(1.4f))
            HeaderCell(strings.testColExpect, Modifier.weight(1.2f))
            HeaderCell(strings.testColActual, Modifier.weight(1.6f))
            HeaderCell("ms", Modifier.width(52.dp))
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.68f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surfaceMuted.copy(alpha = 0.42f))
        ) {
            if (steps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyLogState(strings.testEmpty)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(steps) { index, step ->
                        val result = resultsByIndex[index]
                        ResultRow(
                            index = index,
                            name = step.name,
                            expected = step.expect ?: if (step.match == TestMatchMode.SILENCE) "silence" else "",
                            result = result,
                            passText = strings.testStatusPass,
                            failText = strings.testStatusFail
                        )
                        HorizontalDivider(color = colors.border.copy(alpha = 0.42f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalAppColors.current.onSurface.copy(alpha = 0.52f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun ResultRow(
    index: Int,
    name: String,
    expected: String,
    result: TestSequenceRunner.StepResult?,
    passText: String,
    failText: String
) {
    val colors = LocalAppColors.current
    val statusColor = when {
        result == null -> colors.onSurface.copy(alpha = 0.32f)
        result.passed -> colors.success
        else -> colors.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${index + 1}",
                color = colors.onSurface.copy(alpha = 0.42f),
                fontSize = 10.sp,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = name,
                color = colors.onSurface.copy(alpha = if (result == null) 0.62f else 0.9f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = expected,
            modifier = Modifier.weight(1.2f).padding(end = 8.dp),
            color = colors.onSurface.copy(alpha = 0.62f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result?.actual?.takeIf { it.isNotBlank() } ?: "—",
            modifier = Modifier.weight(1.6f).padding(end = 8.dp),
            color = statusColor.copy(alpha = 0.92f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result?.elapsedMs?.toString() ?: "",
            modifier = Modifier.width(52.dp),
            color = colors.onSurface.copy(alpha = 0.46f),
            fontSize = 10.sp
        )
        Text(
            text = when {
                result == null -> ""
                result.passed -> passText
                else -> failText
            },
            color = statusColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(38.dp)
        )
    }
}
