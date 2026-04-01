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
import com.yiaobang.kserial.theme.LocalAppColors

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.topBar)
            .border(0.dp, colors.border)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Power, null, modifier = Modifier.size(22.dp), tint = colors.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.onSurface))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TopIconBtn(Icons.Default.PushPin, onTop, tint = if (isTop) colors.primary else colors.onSurface.copy(0.6f))
            TopIconBtn(Icons.Default.Translate, onLanguage)
            TopIconBtn(Icons.Default.Palette, onTheme)
            TopIconBtn(Icons.Default.Remove, onMinimize)
            TopIconBtn(Icons.Default.Close, onClose)
        }
    }
}

/**
 * 顶部工具栏的图标按钮
 */
@Composable
private fun TopIconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = LocalAppColors.current.onSurface.copy(0.6f)
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
    }
}
