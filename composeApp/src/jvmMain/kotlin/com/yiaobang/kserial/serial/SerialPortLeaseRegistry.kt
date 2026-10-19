package com.yiaobang.kserial.serial

/**
 * 进程内串口独占登记表。底层驱动是否允许重复打开因平台而异，
 * 因此在终端标签、模拟器和桥接之间先用统一租约给出可预测的冲突行为。
 */
object SerialPortLeaseRegistry {
    private val leases = mutableMapOf<String, String>()

    @Synchronized
    fun acquire(portName: String, owner: String): Boolean {
        val port = leaseKey(portName)
        if (port.isEmpty()) return false
        val currentOwner = leases[port]
        if (currentOwner != null && currentOwner != owner) return false
        leases[port] = owner
        return true
    }

    @Synchronized
    fun release(portName: String, owner: String) {
        val port = leaseKey(portName)
        if (leases[port] == owner) {
            leases.remove(port)
        }
    }

    @Synchronized
    fun ownerOf(portName: String): String? {
        return leases[leaseKey(portName)]
    }

    @Synchronized
    fun clear() {
        leases.clear()
    }

    private val WINDOWS_COM_REGEX = Regex("""^(?:\\\\\.[\/\\])?COM\d+$""", RegexOption.IGNORE_CASE)

    private fun leaseKey(portName: String): String {
        // Windows COM 名不区分大小写，且统一去除 \\.\ 前缀；其他平台保留原名称以免破坏设备路径。
        val trimmed = portName.trim()
        return if (WINDOWS_COM_REGEX.matches(trimmed)) {
            val normalized = trimmed.substring(trimmed.lastIndexOfAny(charArrayOf('\\', '/')) + 1)
            normalized.uppercase()
        } else trimmed
    }
}
