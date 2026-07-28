package com.yiaobang.kserial.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用全局色值定义。
 *
 * 色板保持克制的桌面工具风格：背景负责分区，表面承载内容，强调色只用于
 * 主操作、选中态和连接状态。所有界面组件都从这里取色，避免各页面自行拼色。
 */
data class AppColors(
    val primary: Color,      // 主题高亮色 (用于主按钮)
    val success: Color,      // 成功色 (用于绿色状态灯、统计数字)
    val error: Color,        // 错误色 (用于断开状态、错误提示)
    val background: Color,   // 主背景色
    val surface: Color,      // 容器盒子的背景色 (如输入框、卡片)
    val surfaceMuted: Color, // 次级表面 (标签栏、输入背景和悬浮工具区)
    val onSurface: Color,    // 表面文字主色
    val border: Color,       // 分割线与边框色
    val topBar: Color,       // 标题栏与主导航的应用框架色
    val onTopBar: Color      // 应用框架上的文字与图标色
)

/**
 * 浅色模式。
 */
val CupertinoLightColors = AppColors(
    primary = Color(0xFF2F6BFF),
    success = Color(0xFF168B63),
    error = Color(0xFFD84A5F),
    background = Color(0xFFF2F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1A2333),
    border = Color(0xFFDCE3ED),
    topBar = Color(0xFFF8FAFD),
    onTopBar = Color(0xFF1A2333)
)

/**
 * 深色模式。
 */
val CupertinoDarkColors = AppColors(
    primary = Color(0xFF78A7FF),
    success = Color(0xFF45C996),
    error = Color(0xFFFF7180),
    background = Color(0xFF0F141D),
    surface = Color(0xFF181F2A),
    surfaceMuted = Color(0xFF131A24),
    onSurface = Color(0xFFEDF2F8),
    border = Color(0xFF2A3545),
    topBar = Color(0xFF0C1320),
    onTopBar = Color(0xFFF0F4FC)
)
