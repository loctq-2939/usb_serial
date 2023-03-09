package com.lab.usb_serial

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.lab.usb_serial.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction().replace(R.id.container, TerminalFragment(), null)
            .commit()
    }

    fun usb() {
        // Find all available drivers from attached devices.
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "Available drivers is empty", Toast.LENGTH_LONG).show()
            writeToFile("Available drivers is empty")
            return
        }

        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
            ?: // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return

        val port = driver.ports[0] // Most devices have just one port (port 0)

        try {
            port.open(connection)
            port.setParameters(
                115200,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            with(binding) {
                /*portNumber.text = port.portNumber.toString()
                deviceName.text = port.device?.deviceName
                productName.text = port.device?.productName
                manufacturerName.text = port.device?.manufacturerName
                serialNumber.text = port.device?.serialNumber*/
            }

            port.write("Just connected to devices by android !".toByteArray(), WRITE_WAIT_MILLIS)
            writeToFile("Just connected to devices by android !")
            val response: ByteArray? = null
            port.read(response, READ_WAIT_MILLIS)
            response?.let {
                //binding.data.append("${String(it)} - $response")
            }

            port.close()
        } catch (ex: Exception) {
            Toast.makeText(this, ex.message.toString(), Toast.LENGTH_LONG).show()
            writeToFile(ex.message.toString())
        }
    }

    companion object {
        const val WRITE_WAIT_MILLIS = 1000
        const val READ_WAIT_MILLIS = 1000
    }
}