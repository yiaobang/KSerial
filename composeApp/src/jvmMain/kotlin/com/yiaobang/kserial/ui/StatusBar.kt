package com.yiaobang.kserial.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.model.SerialConnectionState
import com.yiaobang.kserial.theme.LocalAppColors
import kotlinx.coroutines.delay

/** 底部状态栏：集中显示连接信息及可点击清零的 TX/RX 计数与实时活动闪烁灯。 */
@Composable
fun AppStatusBar(vm: SerialViewModel) {
    val s = LocalAppStrings.current
    val colors = LocalAppColors.current

    var txActive by remember { mutableStateOf(false) }
    LaunchedEffect(vm.txCount) {
        if (vm.txCount > 0) {
            txActive = true
            delay(120)
            txActive = false
        }
    }

    var rxActive by remember { mutableStateOf(false) }
    LaunchedEffect(vm.rxCount) {
        if (vm.rxCount > 0) {
            rxActive = true
            delay(120)
            rxActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(colors.surfaceMuted)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 170.dp, max = 230.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val indicatorColor = when (vm.connectionState) {
                        SerialConnectionState.CONNECTED -> colors.success
                        SerialConnectionState.CONNECTING, SerialConnectionState.RECONNECTING -> colors.primary
                        SerialConnectionState.ERROR -> colors.error
                        SerialConnectionState.DISCONNECTED -> colors.onSurface.copy(alpha = 0.28f)
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            when (vm.connectionState) {
                                SerialConnectionState.CONNECTED -> s.statusConnected
                                SerialConnectionState.CONNECTING -> s.statusConnecting
                                SerialConnectionState.RECONNECTING -> s.statusReconnecting
                                SerialConnectionState.ERROR -> s.statusError
                                SerialConnectionState.DISCONNECTED -> s.statusDisconnected
                            },
                            color = colors.onSurface.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (vm.config.selectedPort.isNotBlank()) {
                            Text("·", color = colors.onSurface.copy(alpha = 0.30f), fontSize = 11.sp)
                            Text(
                                vm.config.selectedPort,
                                color = colors.onSurface.copy(alpha = 0.54f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            StatusCounter(s.labelTx, vm.txCount, colors.primary, active = txActive) { vm.clearTx() }
            Box(Modifier.width(1.dp).height(16.dp).background(colors.border.copy(alpha = 0.72f)))
            StatusCounter(s.labelRx, vm.rxCount, colors.success, active = rxActive) { vm.clearRx() }
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.82f)))
    }
}

/** 紧凑状态计数器；带微型硬件 LED 闪烁灯，点击仍可清零。 */
@Composable
private fun StatusCounter(
    label: String,
    value: Long,
    color: Color,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val animatedLedColor by animateColorAsState(
        targetValue = if (active) color else color.copy(alpha = 0.22f),
        animationSpec = tween(durationMillis = if (active) 40 else 180)
    )
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label $value" }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(animatedLedColor)
        )
        Text(label, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = LocalAppColors.current.onSurface.copy(0.56f))
        Text(value.toString(), color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
