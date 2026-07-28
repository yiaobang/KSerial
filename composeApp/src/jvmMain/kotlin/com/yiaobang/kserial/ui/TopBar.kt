package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiaobang.kserial.resources.Res
import com.yiaobang.kserial.resources.app_icon
import com.yiaobang.kserial.i18n.LocalAppStrings
import com.yiaobang.kserial.theme.LocalAppColors
import org.jetbrains.compose.resources.painterResource

/**
 * 自定义顶部标题栏（整合控制按钮）
 */
@Composable
fun AppTopBar(
    title: String,
    isTop: Boolean,
    onTop: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalAppColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.topBar)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(9.dp))
            Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.onTopBar))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                TopIconBtn(Icons.Default.PushPin, strings.actionPinWindow, onTop, tint = if (isTop) colors.primary else colors.onTopBar.copy(0.62f))
                TopIconBtn(Icons.Default.Translate, strings.actionLanguage, onLanguage)
                TopIconBtn(Icons.Default.Palette, strings.actionTheme, onTheme)
                Spacer(Modifier.width(5.dp))
                TopIconBtn(Icons.Default.Remove, strings.actionMinimize, onMinimize)
                TopIconBtn(Icons.Default.Close, strings.actionCloseWindow, onClose)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.86f)))
    }
}

/**
 * 顶部工具栏的图标按钮
 */
@Composable
private fun TopIconBtn(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = LocalAppColors.current.onTopBar.copy(0.62f)
) {
    AppTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, description, modifier = Modifier.size(17.dp), tint = tint)
        }
    }
}
