package com.yiaobang.kserial.i18n

/**
 * 语言枚举 —— 类型安全的语言标识
 * 使用 Kotlin enum 的 entries 和 ordinal 实现自动循环切换
 */
enum class Language {
    ZH, EN, JA;

    /** 循环切换到下一种语言 */
    fun next(): Language = entries[(ordinal + 1) % entries.size]
}

/**
 * 多语言字典 —— 使用 sealed class 保证密封性
 * 每种语言是一个 data object，编译期可穷举，when 表达式无需 else 分支
 */
sealed class AppStrings {
    // 设置区块
    abstract val labelSerialPort: String
    abstract val labelBaudRate: String
    abstract val labelDataBits: String
    abstract val labelStopBits: String
    abstract val labelParity: String
    abstract val labelFlowControl: String

    // 参数选项
    abstract val stopBitsOptions: List<String>
    abstract val parityOptions: List<String>
    abstract val flowControlOptions: List<String>

    // 按钮动作
    abstract val btnOpenPort: String
    abstract val btnClosePort: String
    abstract val btnClearReceive: String
    abstract val btnSendData: String
    abstract val btnClearSend: String
    abstract val btnNewWindow: String
    abstract val btnAnalogReply: String

    // 复选选项
    abstract val optRealTime: String
    abstract val optHexDisplay: String
    abstract val optHexSend: String
    abstract val optLoopSend: String
    abstract val optSaveSend: String
    abstract val optSaveReceive: String

    // 标签与状态
    abstract val labelInterval: String
    abstract val labelMs: String
    abstract val labelTx: String
    abstract val labelRx: String
    abstract val labelStatusPort: String
    abstract val labelStatusSim: String

    /** 中文 */
    data object ZH : AppStrings() {
        override val labelSerialPort = "串口号"
        override val labelBaudRate = "波特率"
        override val labelDataBits = "数据位"
        override val labelStopBits = "停止位"
        override val labelParity = "校验位"
        override val labelFlowControl = "流控"

        override val stopBitsOptions = listOf("1", "1.5", "2")
        override val parityOptions = listOf("None", "Odd", "Even", "Mark", "Space")
        override val flowControlOptions = listOf("None", "RTS/CTS", "DSR/DTR", "XON/XOFF")

        override val btnOpenPort = "打开串口"
        override val btnClosePort = "关闭串口"
        override val btnClearReceive = "清除接收"
        override val btnSendData = "发送数据"
        override val btnClearSend = "清除发送"
        override val btnNewWindow = "新建窗口"
        override val btnAnalogReply = "设备模拟"

        override val optRealTime = "实时显示"
        override val optHexDisplay = "16进制显示"
        override val optHexSend = "16进制发送"
        override val optLoopSend = "循环发送"
        override val optSaveSend = "保存发送"
        override val optSaveReceive = "保存接收"

        override val labelInterval = "发送周期"
        override val labelMs = "毫秒"
        override val labelTx = "发送计数"
        override val labelRx = "接收计数"
        override val labelStatusPort = "当前串口"
        override val labelStatusSim = "当前模拟"
    }

    /** 英文 */
    data object EN : AppStrings() {
        override val labelSerialPort = "Serial Port"
        override val labelBaudRate = "Baud Rate"
        override val labelDataBits = "Data Bits"
        override val labelStopBits = "Stop Bits"
        override val labelParity = "Parity"
        override val labelFlowControl = "Flow Control"

        override val stopBitsOptions = listOf("1", "1.5", "2")
        override val parityOptions = listOf("None", "Odd", "Even", "Mark", "Space")
        override val flowControlOptions = listOf("None", "RTS/CTS", "DSR/DTR", "XON/XOFF")

        override val btnOpenPort = "Open Port"
        override val btnClosePort = "Close Port"
        override val btnClearReceive = "Clear Receive"
        override val btnSendData = "Send Data"
        override val btnClearSend = "Clear Send"
        override val btnNewWindow = "New Window"
        override val btnAnalogReply = "Simulation"

        override val optRealTime = "Show Real-Time"
        override val optHexDisplay = "Hex Display"
        override val optHexSend = "Hex Send"
        override val optLoopSend = "Loop Send"
        override val optSaveSend = "Save TX"
        override val optSaveReceive = "Save RX"

        override val labelInterval = "Interval"
        override val labelMs = "ms"
        override val labelTx = "TX Count"
        override val labelRx = "RX Count"
        override val labelStatusPort = "Port Active"
        override val labelStatusSim = "Sim Active"
    }

    /** 日文 */
    data object JA : AppStrings() {
        override val labelSerialPort = "シリアルポート"
        override val labelBaudRate = "ボーレート"
        override val labelDataBits = "データビット"
        override val labelStopBits = "ストップビット"
        override val labelParity = "パリティ"
        override val labelFlowControl = "フロー制御"

        override val stopBitsOptions = listOf("1", "1.5", "2")
        override val parityOptions = listOf("なし", "奇数", "偶数", "マーク", "スペース")
        override val flowControlOptions = listOf("なし", "RTS/CTS", "DSR/DTR", "XON/XOFF")

        override val btnOpenPort = "開く"
        override val btnClosePort = "閉じる"
        override val btnClearReceive = "受信クリア"
        override val btnSendData = "データ送信"
        override val btnClearSend = "送信エリアクリア"
        override val btnNewWindow = "新規ウィンドウ"
        override val btnAnalogReply = "デバイス模擬"

        override val optRealTime = "リアルタイム表示"
        override val optHexDisplay = "HEX表示"
        override val optHexSend = "HEX送信"
        override val optLoopSend = "繰り返し送信"
        override val optSaveSend = "送信保存"
        override val optSaveReceive = "受信保存"

        override val labelInterval = "送信間隔"
        override val labelMs = "ミリ秒"
        override val labelTx = "送信済み"
        override val labelRx = "受信済み"
        override val labelStatusPort = "ポート動作中"
        override val labelStatusSim = "シミュレーション中"
    }

    companion object {
        /** 根据语言枚举获取对应字典，编译期穷举安全 */
        fun of(language: Language): AppStrings = when (language) {
            Language.ZH -> ZH
            Language.EN -> EN
            Language.JA -> JA
        }
    }
}
