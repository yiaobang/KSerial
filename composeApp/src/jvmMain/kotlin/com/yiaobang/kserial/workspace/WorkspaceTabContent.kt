package com.yiaobang.kserial.workspace

import com.yiaobang.kserial.SerialViewModel
import com.yiaobang.kserial.feature.GatewayViewModel
import com.yiaobang.kserial.i18n.Language

/**
 * 左侧导航中的功能域。枚举只表达稳定的导航身份；每个标签页的运行态由
 * [WorkspaceTabContent] 明确承载，避免在标签上不断增加互斥的可空 ViewModel。
 */
enum class WorkspaceSection(val fallbackTitle: String) {
    Basic("串口终端"),
    Simulator("设备模拟"),
    Gateway("串口桥接");

    fun localizedTitle(language: Language): String = when (language) {
        Language.ZH -> fallbackTitle
        Language.EN -> when (this) {
            Basic -> "Terminal"
            Simulator -> "Simulator"
            Gateway -> "Gateway"
        }
        Language.JA -> when (this) {
            Basic -> "ターミナル"
            Simulator -> "シミュレータ"
            Gateway -> "ゲートウェイ"
        }
    }
}

/**
 * 标签页拥有的业务会话。新增功能时应增加新的内容类型，由它负责自己的
 * 状态传播和释放，而不是继续向 AppTab 添加互斥的可空 ViewModel 字段。
 */
sealed interface WorkspaceTabContent {
    val section: WorkspaceSection
    val selectedPort: String?
        get() = null

    fun applyTheme(isDark: Boolean) = Unit
    fun applyLanguage(language: Language) = Unit
    fun dispose()
}

/** 终端与模拟器共用串口会话能力，但仍保持不同的页面内容类型。 */
sealed class SerialWorkspaceTabContent(
    final override val section: WorkspaceSection,
    val viewModel: SerialViewModel
) : WorkspaceTabContent {
    final override val selectedPort: String?
        get() = viewModel.config.selectedPort.ifBlank { null }

    final override fun applyTheme(isDark: Boolean) {
        viewModel.prefs.isDark = isDark
    }

    final override fun applyLanguage(language: Language) {
        viewModel.prefs.currentLanguage = language
    }

    final override fun dispose() {
        viewModel.onCleared()
    }
}

class BasicTabContent(viewModel: SerialViewModel) :
    SerialWorkspaceTabContent(WorkspaceSection.Basic, viewModel)

class SimulatorTabContent(viewModel: SerialViewModel) :
    SerialWorkspaceTabContent(WorkspaceSection.Simulator, viewModel)

class GatewayTabContent(
    val viewModel: GatewayViewModel
) : WorkspaceTabContent {
    override val section = WorkspaceSection.Gateway

    override fun applyLanguage(language: Language) {
        viewModel.language = language
    }

    override fun dispose() {
        viewModel.onCleared()
    }
}

/** 一个可在主窗口和浮动窗口之间移动的业务标签页。 */
class AppTab(
    val id: Int,
    val title: String,
    val titleIndex: Int,
    val content: WorkspaceTabContent
) {
    val section: WorkspaceSection
        get() = content.section
}
