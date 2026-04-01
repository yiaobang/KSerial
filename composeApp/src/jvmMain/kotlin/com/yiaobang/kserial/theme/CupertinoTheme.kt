package com.yiaobang.kserial.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * 全局主题状态持有者 (使用 CompositionLocal 实现状态穿透)
 */
val LocalAppColors = staticCompositionLocalOf { CupertinoLightColors }

/**
 * 自定义应用主题容器 (1:1 复刻 AtlantaFX 样式)
 */
@Composable
fun CupertinoTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    // 决定当前使用的色板
    val targetColors = if (isDark) CupertinoDarkColors else CupertinoLightColors
    
    // 【平滑切换黑科技】为每个颜色属性添加 500ms 的补间动画，实现明暗切换时的“呼吸感”
    val animatedColors = AppColors(
        primary = animateColorAsState(targetColors.primary, tween(500)).value,
        success = animateColorAsState(targetColors.success, tween(500)).value,
        error = animateColorAsState(targetColors.error, tween(500)).value,
        background = animateColorAsState(targetColors.background, tween(500)).value,
        surface = animateColorAsState(targetColors.surface, tween(500)).value,
        onSurface = animateColorAsState(targetColors.onSurface, tween(500)).value,
        border = animateColorAsState(targetColors.border, tween(500)).value,
        topBar = animateColorAsState(targetColors.topBar, tween(500)).value
    )

    // 通过 CompositionLocalProvider 向下分发自定义色板
    CompositionLocalProvider(LocalAppColors provides animatedColors) {
        MaterialTheme(
            // 兼容部分 Material 3 组件的基础配色
            colorScheme = if (isDark) darkColorScheme(primary = animatedColors.primary) 
                          else lightColorScheme(primary = animatedColors.primary),
            // 默认全案使用等宽字体 (适合串口工具显示)
            typography = Typography(
                bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            ),
            content = content
        )
    }
}
