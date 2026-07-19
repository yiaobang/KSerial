package com.yiaobang.kserial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.topBar)
            .border(0.dp, colors.border)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.onSurface))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TopIconBtn(Icons.Default.PushPin, strings.actionPinWindow, onTop, tint = if (isTop) colors.primary else colors.onSurface.copy(0.6f))
            TopIconBtn(Icons.Default.Translate, strings.actionLanguage, onLanguage)
            TopIconBtn(Icons.Default.Palette, strings.actionTheme, onTheme)
            TopIconBtn(Icons.Default.Remove, strings.actionMinimize, onMinimize)
            TopIconBtn(Icons.Default.Close, strings.actionCloseWindow, onClose)
        }
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
    tint: Color = LocalAppColors.current.onSurface.copy(0.6f)
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, modifier = Modifier.size(18.dp), tint = tint)
    }
}
