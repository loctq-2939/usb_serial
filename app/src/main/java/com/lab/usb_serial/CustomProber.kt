package com.lab.usb_serial

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber


object CustomProber {
    // e.g. Digispark CDC]

    fun customProber(): UsbSerialProber {
        val customTable = ProbeTable()
        customTable.addProduct(
            0x16d0,
            0x087e,
            CdcAcmSerialDriver::class.java
        ) // e.g. Digispark CDC
        return UsbSerialProber(customTable)
    }
}