package com.yiaobang.kserial.serial

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerialPortLeaseRegistryTest {
    @AfterTest
    fun clearRegistry() = SerialPortLeaseRegistry.clear()

    @Test
    fun normalizesWindowsPortNamesAndAllowsIdempotentOwner() {
        assertTrue(SerialPortLeaseRegistry.acquire(" com7 ", "owner-a"))
        assertTrue(SerialPortLeaseRegistry.acquire("COM7", "owner-a"))
        assertFalse(SerialPortLeaseRegistry.acquire("com7", "owner-b"))
        assertEquals("owner-a", SerialPortLeaseRegistry.ownerOf("CoM7"))
    }

    @Test
    fun onlyTheCurrentOwnerCanReleaseAndBlankPortsAreRejected() {
        assertFalse(SerialPortLeaseRegistry.acquire("   ", "owner"))
        assertTrue(SerialPortLeaseRegistry.acquire("/dev/ttyUSB0", "owner-a"))
        SerialPortLeaseRegistry.release("/dev/ttyUSB0", "owner-b")
        assertEquals("owner-a", SerialPortLeaseRegistry.ownerOf("/dev/ttyUSB0"))
        SerialPortLeaseRegistry.release("/dev/ttyUSB0", "owner-a")
        assertNull(SerialPortLeaseRegistry.ownerOf("/dev/ttyUSB0"))
    }
}
