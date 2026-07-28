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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局主题状态持有者 (使用 CompositionLocal 实现状态穿透)
 */
val LocalAppColors = staticCompositionLocalOf { CupertinoLightColors }

/**
 * 自定义应用主题容器。
 */
@Composable
fun CupertinoTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    // 决定当前使用的色板
    val targetColors = if (isDark) CupertinoDarkColors else CupertinoLightColors
    
    // 为每个颜色属性添加短补间动画，避免明暗主题切换时突变
    val animatedColors = AppColors(
        primary = animateColorAsState(targetColors.primary, tween(180)).value,
        success = animateColorAsState(targetColors.success, tween(180)).value,
        error = animateColorAsState(targetColors.error, tween(180)).value,
        background = animateColorAsState(targetColors.background, tween(180)).value,
        surface = animateColorAsState(targetColors.surface, tween(180)).value,
        surfaceMuted = animateColorAsState(targetColors.surfaceMuted, tween(180)).value,
        onSurface = animateColorAsState(targetColors.onSurface, tween(180)).value,
        border = animateColorAsState(targetColors.border, tween(180)).value,
        topBar = animateColorAsState(targetColors.topBar, tween(180)).value,
        onTopBar = animateColorAsState(targetColors.onTopBar, tween(180)).value
    )

    // 通过 CompositionLocalProvider 向下分发自定义色板
    CompositionLocalProvider(LocalAppColors provides animatedColors) {
        MaterialTheme(
            // 完整映射 Material 组件颜色，避免 Checkbox、Button 等回落到默认紫色色板。
            colorScheme = if (isDark) {
                darkColorScheme(
                    primary = animatedColors.primary,
                    onPrimary = Color(0xFF071426),
                    primaryContainer = animatedColors.primary.copy(alpha = 0.18f),
                    onPrimaryContainer = animatedColors.onSurface,
                    secondary = animatedColors.primary,
                    background = animatedColors.background,
                    onBackground = animatedColors.onSurface,
                    surface = animatedColors.surface,
                    onSurface = animatedColors.onSurface,
                    surfaceVariant = animatedColors.surfaceMuted,
                    onSurfaceVariant = animatedColors.onSurface.copy(alpha = 0.72f),
                    outline = animatedColors.border,
                    error = animatedColors.error
                )
            } else {
                lightColorScheme(
                    primary = animatedColors.primary,
                    onPrimary = Color.White,
                    primaryContainer = animatedColors.primary.copy(alpha = 0.10f),
                    onPrimaryContainer = animatedColors.primary,
                    secondary = animatedColors.primary,
                    background = animatedColors.background,
                    onBackground = animatedColors.onSurface,
                    surface = animatedColors.surface,
                    onSurface = animatedColors.onSurface,
                    surfaceVariant = animatedColors.surfaceMuted,
                    onSurfaceVariant = animatedColors.onSurface.copy(alpha = 0.72f),
                    outline = animatedColors.border,
                    error = animatedColors.error
                )
            },
            // 桌面工具使用紧凑但清晰的固定字号；日志区域仍单独使用等宽字体。
            typography = Typography(
                titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                titleSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                bodyMedium = TextStyle(fontSize = 13.sp),
                bodySmall = TextStyle(fontSize = 11.sp),
                labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
            ),
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ),
            content = content
        )
    }
}
