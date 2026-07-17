package com.yiaobang.kserial.serial

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

    private fun leaseKey(portName: String): String {
        val trimmed = portName.trim()
        return if (trimmed.matches(Regex("COM\\d+", RegexOption.IGNORE_CASE))) trimmed.uppercase() else trimmed
    }
}
