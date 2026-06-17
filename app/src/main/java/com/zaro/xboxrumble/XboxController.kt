package com.zaro.xboxrumble

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

object XboxController {
    private const val TAG = "XboxRumble"
    const val VENDOR_MICROSOFT = 0x045E
    val SUPPORTED_PIDS = setOf(
        0x02D1, 0x02DD, 0x02E3, 0x02EA,
        0x02FF, 0x0B00, 0x0B12, 0x0B13,
    )
    fun isXboxController(dev: UsbDevice): Boolean =
        dev.vendorId == VENDOR_MICROSOFT && dev.productId in SUPPORTED_PIDS
    fun findController(usbManager: UsbManager): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { isXboxController(it) }
    class Session internal constructor(
        val device: UsbDevice,
        private val connection: UsbDeviceConnection,
        private val iface: UsbInterface,
        private val endpointOut: UsbEndpoint?,
    ) {
        fun close() {
            try { connection.releaseInterface(iface) } catch (_: Throwable) {}
            try { connection.close() } catch (_: Throwable) {}
        }
        fun sendRumble(heavy: Int): Boolean {
            val h = heavy.coerceIn(0, 255)
            val packet = byteArrayOf(
                0x09.toByte(), 0x00, 0x00,
                0x00, 0x00,
                h.toByte(),
                0x00,
                0xFF.toByte(),
                0x00,
            )
            if (endpointOut != null) {
                val w = connection.bulkTransfer(endpointOut, packet, packet.size, 100)
                if (w >= 0) return true
            }
            val res = connection.controlTransfer(
                0x21, 0x09, 0x0209, iface.id, packet, packet.size, 100,
            )
            return res >= 0
        }
    }
    fun openSession(usbManager: UsbManager, device: UsbDevice): Session? {
        if (!isXboxController(device)) return null
        var chosenIface: UsbInterface? = null
        var chosenOut: UsbEndpoint? = null
        for (i in 0 until device.interfaceCount) {
            val ifc = device.getInterface(i)
            for (e in 0 until ifc.endpointCount) {
                val ep = ifc.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                ) { chosenIface = ifc; chosenOut = ep; break }
            }
            if (chosenIface != null) break
        }
        if (chosenIface == null && device.interfaceCount > 0)
            chosenIface = device.getInterface(0)
        if (chosenIface == null) return null
        val conn = usbManager.openDevice(device) ?: return null
        if (!conn.claimInterface(chosenIface, true)) {
            conn.close(); return null
        }
        return Session(device, conn, chosenIface, chosenOut)
    }
}