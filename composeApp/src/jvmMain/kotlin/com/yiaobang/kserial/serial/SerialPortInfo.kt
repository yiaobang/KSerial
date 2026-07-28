package com.yiaobang.kserial.serial

/** 系统端口名及驱动提供的友好描述，用于在下拉框中区分同类设备。 */
data class SerialPortInfo(
    val systemName: String,
    val descriptiveName: String,
    val description: String,
    val vendorId: Int? = null,
    val productId: Int? = null
) {
    val displayName: String
        get() {
            val friendly = descriptiveName.takeIf { it.isNotBlank() && !it.equals(systemName, ignoreCase = true) }
                ?: description.takeIf { it.isNotBlank() && !it.equals(systemName, ignoreCase = true) }
            val usbId = if (vendorId != null && productId != null) {
                "VID:%04X PID:%04X".format(vendorId, productId)
            } else {
                null
            }
            return listOfNotNull(systemName, friendly, usbId).joinToString(" · ")
        }
}

/** 底层主动上报的连接异常，避免只依赖周期端口扫描。 */
sealed interface SerialTransportEvent {
    data object Disconnected : SerialTransportEvent
    data class Error(val detail: String) : SerialTransportEvent
}
