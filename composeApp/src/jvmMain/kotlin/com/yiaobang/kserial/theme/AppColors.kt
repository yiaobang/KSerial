package com.yiaobang.kserial.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用全局色值定义 (1:1 复刻 Apple 设计语言)
 * 职责：定义浅色与深色两种模式下的核心视觉令牌。
 */
data class AppColors(
    val primary: Color,      // 主题高亮色 (用于主按钮)
    val success: Color,      // 成功色 (用于绿色状态灯、统计数字)
    val error: Color,        // 错误色 (用于断开状态、错误提示)
    val background: Color,   // 主背景色
    val surface: Color,      // 容器盒子的背景色 (如输入框、卡片)
    val onSurface: Color,    // 表面文字主色
    val border: Color,       // 分割线与边框色
    val topBar: Color        // 沉浸式标题栏背景色
)

/**
 * 极简浅色模式 (Light Mode)
 * 基于 Cupertino 标准系统色
 */
val CupertinoLightColors = AppColors(
    primary = Color(0xFF007AFF),   // 系统蓝
    success = Color(0xFF34C759),   // 系统绿
    error = Color(0xFFFF3B30),     // 系统红
    background = Color(0xFFF5F7FA), // 背景灰
    surface = Color.White,         // 纯白表面
    onSurface = Color(0xFF171A1F), // 深灰文字
    border = Color(0xFFD5DAE1),    // 灰色边框
    topBar = Color(0xFFEDF0F4)     // 略深的标题栏
)

/**
 * 极简深色模式 (Dark Mode)
 * 基于 Cupertino 高对比度深色系统色
 */
val CupertinoDarkColors = AppColors(
    primary = Color(0xFF0A84FF),   // 深色蓝
    success = Color(0xFF30D158),   // 深色绿
    error = Color(0xFFFF453A),     // 深色红
    background = Color(0xFF0E1116), // 深色背景
    surface = Color(0xFF171B22),   // 灰黑色表面
    onSurface = Color(0xFFF2F4F7), // 浅色文字
    border = Color(0xFF303742),    // 深色边框
    topBar = Color(0xFF141820)     // 标题栏
)
