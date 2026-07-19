package com.yiaobang.kserial.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yiaobang.kserial.i18n.Language

/**
 * UI 偏好设置状态持有类
 * 集中管理与串口通信无关的界面选项和用户偏好
 */
class AppPreferences {
    /** 实时显示开关 */
    var showRealTime by mutableStateOf(true)

    /** HEX 模式显示切换 */
    var hexDisplay by mutableStateOf(false)

    /** HEX 发送启用开关 */
    var hexSend by mutableStateOf(false)

    /** 循环发送总开关 */
    var loopSendEnabled by mutableStateOf(false)

    /** 发送周期输入文本 */
    var loopIntervalText by mutableStateOf("1000")

    /** 解析后的发送间隔毫秒数，最小为 1ms */
    val loopIntervalOrNull: Long?
        get() = loopIntervalText.toLongOrNull()?.takeIf { it in 1L..86_400_000L }

    val loopInterval: Long get() = loopIntervalOrNull ?: 1000L

    val isLoopIntervalValid: Boolean get() = loopIntervalOrNull != null

    /** 持久化保存发送数据 */
    var saveSend by mutableStateOf(false)

    /** 持久化保存接收数据 */
    var saveReceive by mutableStateOf(false)

    /** 快捷发送模板，每行一条命令 */
    var quickCommandsText by mutableStateOf("AT\\r\\n\nAT+VERSION\\r\\n")

    /** 窗口置顶状态 */
    var isAlwaysOnTop by mutableStateOf(false)

    /** 深色主题开关 */
    var isDark by mutableStateOf(false)

    /** 当前语言 */
    var currentLanguage by mutableStateOf(Language.fromLocale())

    /** 日志关键词高亮 */
    val highlight = KeywordHighlightConfig()

    /** 日志过滤与滚动控制 */
    val logFilter = LogFilterConfig()

    /** 切换到下一种语言 */
    fun switchLanguage() {
        currentLanguage = currentLanguage.next()
    }

    /** 切换主题 */
    fun switchTheme() {
        isDark = !isDark
    }
}
