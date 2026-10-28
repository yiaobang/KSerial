package com.yiaobang.kserial.serial

/**
 * 端点地址：串口名，或者网络端点 URL。
 *
 * 支持的写法（大小写不敏感）：
 * - `COM3` / `/dev/ttyUSB0`：物理串口
 * - `tcp://192.168.1.20:8080`：作为客户端连接远端串口服务器
 * - `tcp://:9000`、`tcp-listen://9000`：作为服务端监听，等待对端接入
 * - `udp://192.168.1.20:9000`：向固定对端发 UDP（串口转 UDP 模块常见）
 * - `udp-listen://9000`：监听 UDP 端口，用首个报文的对端地址回发
 *
 * 网络端点忽略波特率/校验等串口参数（对端设备自己负责物理层）。
 */
sealed interface EndpointAddress {

    data class Serial(val portName: String) : EndpointAddress

    data class TcpClient(val host: String, val port: Int) : EndpointAddress

    data class TcpServer(val port: Int) : EndpointAddress

    data class UdpPeer(val host: String, val port: Int) : EndpointAddress

    data class UdpListen(val port: Int) : EndpointAddress

    val isNetwork: Boolean
        get() = this !is Serial

    companion object {
        private const val TCP = "tcp://"
        private const val TCP_LISTEN = "tcp-listen://"
        private const val TCP_SERVER = "tcp-server://"
        private const val UDP = "udp://"
        private const val UDP_LISTEN = "udp-listen://"

        /** 地址里是否带协议前缀——用于跳过串口枚举和热插拔检查。 */
        fun looksLikeNetwork(raw: String): Boolean = raw.contains("://")

        /**
         * 端口监视用判定：这个端点是否已经从系统串口列表里消失。
         * 网络端点本来就不在枚举结果中，永远不算消失——否则打开 tcp:// 后，
         * 任何一次端口列表刷新都会把连接误判为掉线并断开。
         */
        fun lostFromPortList(port: String, availablePorts: Collection<String>): Boolean =
            !looksLikeNetwork(port) && port !in availablePorts

        /**
         * 解析地址；网络地址写错（端口缺失、越界）返回 null，由调用方给出明确错误。
         * 普通字符串一律按串口名返回，保持既有行为不变。
         */
        fun parse(raw: String): EndpointAddress? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val lower = text.lowercase()
            return when {
                lower.startsWith(TCP_LISTEN) || lower.startsWith(TCP_SERVER) ->
                    parsePort(text.substringAfter("://"))?.let { TcpServer(it) }

                lower.startsWith(TCP) -> parseHostPort(text.substring(TCP.length))?.let { (host, port) ->
                    if (host.isEmpty()) TcpServer(port) else TcpClient(host, port)
                }

                lower.startsWith(UDP_LISTEN) ->
                    parsePort(text.substringAfter("://"))?.let { UdpListen(it) }

                lower.startsWith(UDP) -> parseHostPort(text.substring(UDP.length))?.let { (host, port) ->
                    if (host.isEmpty()) UdpListen(port) else UdpPeer(host, port)
                }

                else -> Serial(text)
            }
        }

        /** `host:port`，host 允许为空（表示监听全部网卡）；只有端口时返回空 host。 */
        private fun parseHostPort(text: String): Pair<String, Int>? {
            val separator = text.lastIndexOf(':')
            if (separator < 0) {
                val port = parsePort(text) ?: return null
                return "" to port
            }
            val host = text.substring(0, separator).trim().removeSurrounding("[", "]")
            val port = parsePort(text.substring(separator + 1)) ?: return null
            return host to port
        }

        private fun parsePort(text: String): Int? {
            val value = text.trim().trimStart(':').toIntOrNull() ?: return null
            return value.takeIf { it in 1..65535 }
        }
    }
}
