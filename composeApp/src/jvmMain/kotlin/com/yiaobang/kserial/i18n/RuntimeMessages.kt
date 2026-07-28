package com.yiaobang.kserial.i18n

/** ViewModel 和串口回调使用的三语运行消息；这些位置不能读取 Compose 的字符串资源。 */
object RuntimeMessages {
    fun portDisconnected(language: Language, port: String) = language.pick(
        "串口 $port 已断开", "Serial port $port disconnected", "シリアルポート $port が切断されました"
    )

    fun portOccupied(language: Language, port: String, owner: String) = language.pick(
        "$port 已被 $owner 占用", "$port is already in use by $owner", "$port は $owner が使用中です"
    )

    fun otherPage(language: Language) = language.pick("其他页面", "another page", "別のページ")

    fun connectionFailed(language: Language, port: String, detail: String) = language.pick(
        "连接失败：$port$detail", "Connection failed: $port$detail", "接続に失敗しました：$port$detail"
    )

    fun sendFailed(language: Language, detail: String) = language.pick(
        "发送失败，连接已关闭：$detail", "Send failed; the connection was closed: $detail", "送信に失敗したため接続を閉じました：$detail"
    )

    fun connectionLost(language: Language, detail: String) = language.pick(
        "串口连接中断：$detail", "Serial connection lost: $detail", "シリアル接続が切断されました：$detail"
    )

    fun lineEvent(language: Language, detail: String) = language.pick(
        "串口线路事件：$detail", "Serial line event: $detail", "シリアル回線イベント：$detail"
    )

    fun receiveDropped(language: Language, bytes: Long) = language.pick(
        "接收显示队列过载，丢弃 $bytes 字节", "Receive display queue dropped $bytes bytes", "受信表示キューで $bytes バイトを破棄しました"
    )

    fun receiveRemainderDropped(language: Language, bytes: Long) = language.pick(
        "未完成接收帧超过上限，丢弃 $bytes 字节", "Receive frame remainder dropped $bytes bytes", "未完了の受信フレームから $bytes バイトを破棄しました"
    )

    fun invalidReceiveFraming(language: Language) = language.pick(
        "接收分帧参数无效，当前数据按接收块显示", "Invalid receive framing; current chunks are shown", "受信フレーム設定が無効なため、現在のブロックを表示します"
    )

    fun simulatorLoaded(language: Language, file: String) = language.pick(
        "设备模拟配置已加载：$file", "Device simulation configuration loaded: $file", "デバイス模擬設定を読み込みました：$file"
    )

    fun simulatorFailed(language: Language) = language.pick(
        "设备模拟配置解析失败", "Device simulation configuration could not be parsed", "デバイス模擬設定を解析できませんでした"
    )

    fun invalidLoopInterval(language: Language) = language.pick(
        "循环发送间隔必须在 1 到 86400000 毫秒之间",
        "Loop interval must be between 1 and 86400000 milliseconds",
        "ループ送信間隔は 1～86400000 ミリ秒で指定してください"
    )

    fun loopStopped(language: Language, detail: String) = language.pick(
        "循环发送已停止：$detail", "Loop sending stopped: $detail", "ループ送信を停止しました：$detail"
    )

    fun invalidHex(language: Language) = language.pick(
        "HEX 输入无效", "Invalid HEX input", "HEX 入力が無効です"
    )

    fun disconnected(language: Language) = language.pick("连接已断开", "Disconnected", "接続を切断しました")

    fun gatewayLoaded(language: Language, file: String) = language.pick(
        "桥接配置已加载：$file", "Gateway configuration loaded: $file", "ゲートウェイ設定を読み込みました：$file"
    )

    fun gatewayLoadFailed(language: Language, detail: String) = language.pick(
        "桥接配置加载失败：$detail", "Gateway configuration failed to load: $detail", "ゲートウェイ設定を読み込めませんでした：$detail"
    )

    fun endpointNoPort(language: Language, endpoint: String) = language.pick(
        "$endpoint：未选择串口", "$endpoint: no serial port selected", "$endpoint：シリアルポートが選択されていません"
    )

    fun endpointDuplicate(language: Language, endpoint: String, port: String) = language.pick(
        "$endpoint：串口 $port 在配置中重复", "$endpoint: serial port $port is duplicated", "$endpoint：シリアルポート $port が重複しています"
    )

    fun endpointOpened(language: Language, endpoint: String, port: String, success: Boolean) = if (success) language.pick(
        "$endpoint：$port 已打开", "$endpoint: $port opened", "$endpoint：$port を開きました"
    ) else language.pick(
        "$endpoint：$port 打开失败", "$endpoint: failed to open $port", "$endpoint：$port を開けませんでした"
    )

    fun endpointClosed(language: Language, endpoint: String) = language.pick(
        "$endpoint：已关闭", "$endpoint: closed", "$endpoint：閉じました"
    )

    fun endpointDefaultName(language: Language, index: Int) = language.pick(
        "端点$index", "Endpoint $index", "エンドポイント$index"
    )

    fun forwardingFailed(language: Language, source: String, target: String) = language.pick(
        "$source → $target 转发失败，目标连接已关闭",
        "$source → $target forwarding failed; the target was closed",
        "$source → $target の転送に失敗したため、対象を閉じました"
    )

    fun forwarded(language: Language, source: String, target: String, content: String) = language.pick(
        "$source → $target：$content",
        "$source → $target: $content",
        "$source → $target：$content"
    )

    fun gatewayDropped(language: Language, endpoint: String, bytes: Long) = language.pick(
        "$endpoint：接收队列过载，丢弃 $bytes 字节",
        "$endpoint: receive queue dropped $bytes bytes",
        "$endpoint：受信キューで $bytes バイトを破棄しました"
    )

    private fun Language.pick(zh: String, en: String, ja: String): String = when (this) {
        Language.ZH -> zh
        Language.EN -> en
        Language.JA -> ja
    }
}
