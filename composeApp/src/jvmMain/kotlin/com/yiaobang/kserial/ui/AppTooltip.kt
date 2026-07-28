package com.yiaobang.kserial.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

/** 桌面端图标操作的统一悬停说明，提示文案由调用方提供当前语言资源。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    label: String,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip {
                Text(text = label, fontSize = 11.sp)
            }
        },
        state = rememberTooltipState(),
        content = content
    )
}
