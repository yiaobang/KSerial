package com.yiaobang.kserial.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.theme.LocalAppColors

/**
 * 底部分色状态栏
 * 左半部显示发送计数（点击清零），右半部显示接收计数（点击清零）
 */
@Composable
fun AppStatusBar(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(BorderStroke(1.dp, colors.border.copy(0.3f)))
    ) {
        // 左：发送统计
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.primary.copy(alpha = 0.12f))
                .clickable { vm.clearTx() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.labelTx, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.onSurface.copy(0.7f))
                Spacer(Modifier.width(10.dp))
                Text(vm.txCount.toString(), color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        // 右：接收统计
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.success.copy(alpha = 0.12f))
                .clickable { vm.clearRx() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.labelRx, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.onSurface.copy(0.7f))
                Spacer(Modifier.width(10.dp))
                Text(vm.rxCount.toString(), color = colors.success, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
