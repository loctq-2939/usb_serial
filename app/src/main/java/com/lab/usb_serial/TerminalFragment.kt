package com.lab.usb_serial

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.fondesa.kpermissions.allGranted
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.extension.send
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialPort.ControlLine
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.lab.usb_serial.BuildConfig.APPLICATION_ID
import com.lab.usb_serial.HexDump.dumpHexString
import com.lab.usb_serial.databinding.FragmentTerminalBinding
import java.io.IOException
import java.util.EnumSet


class TerminalFragment : Fragment(R.layout.fragment_terminal), SerialInputOutputManager.Listener {

    private enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    private var baudRate: Int = 115200
    private val withIoManager = false

    private var broadcastReceiver: BroadcastReceiver? = null
    private var mainLooper: Handler? = null

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    var connected = false

    private var controlLines: ControlLines? = null

    lateinit var binding: FragmentTerminalBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTerminalBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (INTENT_ACTION_GRANT_USB == intent.action) {
                    usbPermission = if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false
                        )
                    ) UsbPermission.Granted else UsbPermission.Denied
                    connect()
                }
            }
        }
        mainLooper = Handler(Looper.getMainLooper())
        controlLines = ControlLines(view)
        if (SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val uri = Uri.parse("package:$APPLICATION_ID")
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    ),
                    1999
                )
            } else {
                if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
                    mainLooper?.post(this::connect)
            }
        } else {
            startPermissionsCheck()
        }
        setupView()
    }

    private fun startPermissionsCheck() {
        permissionsBuilder(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).build().send { result ->
            if (result.allGranted()) {
                if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
                    mainLooper?.post(this::connect)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1999) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
                        mainLooper?.post(this::connect)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Allow permission for storage access!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupView() {
        with(binding) {
            btSend.setOnClickListener {
                edtMessage.text?.let {
                    if (it.isNotEmpty()) {
                        send(it.toString())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(broadcastReceiver, IntentFilter(INTENT_ACTION_GRANT_USB))
    }

    override fun onPause() {
        if (connected) {
            status("disconnected")
            disconnect()
        }
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            mainLooper?.post { receive(it) }
        }
    }

    override fun onRunError(e: Exception?) {
        mainLooper?.post {
            status("connection lost: " + e?.message)
            disconnect()
        }
    }

    private fun connect() {
        val device: UsbDevice? = null
        val usbManager = requireActivity().getSystemService(Context.USB_SERVICE) as UsbManager
        /*for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        if (device == null) {
            status("connection failed: device not found")
            return
        }*/
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            status("connection failed: device not found")
            writeToFile("connection failed: device not found")
            return
        }
        // Open a connection to the first available driver.
        var driver = availableDrivers[0]
        if (driver == null) {
            driver = CustomProber.customProber().probeDevice(device)
        }
        if (driver == null) {
            status("connection failed: no driver for device")
            writeToFile("connection failed: no driver for device")
            return
        }
        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && usbPermission === UsbPermission.Unknown && !usbManager.hasPermission(
                driver.device
            )
        ) {
            usbPermission = UsbPermission.Requested
            val flags =
                if (SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val usbPermissionIntent =
                PendingIntent.getBroadcast(activity, 0, Intent(INTENT_ACTION_GRANT_USB), flags)
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }

        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.device)) {
                status("connection failed: permission denied")
                writeToFile("connection failed: permission denied")
            } else {
                status(
                    "connection failed: open failed"
                )
                writeToFile("connection failed: open failed")
            }
            return
        }

        try {
            usbSerialPort = driver.ports[0] // Most devices have just one port (port 0)
            usbSerialPort?.open(usbConnection)
            usbSerialPort?.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            if (withIoManager) {
                usbIoManager = SerialInputOutputManager(usbSerialPort, this)
                usbIoManager?.start()
            }
            status("connected")
            writeToFile("connected")
            connected = true
            controlLines?.start()
        } catch (e: java.lang.Exception) {
            status("connection failed: " + e.message)
            writeToFile("connection failed: ${e.message}")
            disconnect()
        }
    }

    private fun disconnect() {
        connected = false
        controlLines?.stop()
        usbIoManager?.apply {
            listener = null
            stop()
        }
        usbIoManager = null
        try {
            usbSerialPort?.close()
        } catch (ignored: IOException) {
            writeToFile("disconnect: ${ignored.message}")
        }
        usbSerialPort = null
    }

    private fun send(str: String) {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            writeToFile("not connected")
            return
        }
        try {
            val data = str.toByteArray()
            val spn = SpannableStringBuilder()
            spn.append(
                """send ${data.size} bytes"""
            )
            spn.append(dumpHexString(data)).append("\n")
            spn.setSpan(
                ForegroundColorSpan(ResourcesCompat.getColor(resources, R.color.black, null)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.receiveText.append(spn)
            writeToFile("$spn --byteArray: $data")
            usbSerialPort?.write(data, WRITE_WAIT_MILLIS)
        } catch (e: java.lang.Exception) {
            onRunError(e)
            writeToFile("catch connection lost: " + e.message)
        }
    }

    private fun read() {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            writeToFile("not connected")
            return
        }
        try {
            val buffer = ByteArray(8192)
            val len = usbSerialPort?.read(buffer, READ_WAIT_MILLIS)
            len?.let {
                receive(buffer.copyOf(it))
            }
        } catch (e: IOException) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.message)
            writeToFile("connection lost: " + e.message)
            disconnect()
        }
    }

    private fun receive(data: ByteArray) {
        val spn = SpannableStringBuilder()
        spn.append(
            """receive ${data.size} bytes"""
        )
        if (data.isNotEmpty()) spn.append(dumpHexString(data)).append("\n")
        binding.receiveText.append(spn)
        writeToFile(spn.toString())
    }

    fun status(str: String) {
        val spn = SpannableStringBuilder(
            """
              $str
              
              """.trimIndent()
        )
        spn.setSpan(
            ForegroundColorSpan(ResourcesCompat.getColor(resources, R.color.black, null)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.receiveText.append(spn)
    }

    inner class ControlLines(view: View) {

        private val runnable: Runnable

        init {
            runnable = Runnable {
                this.run()
            } // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks
            with(binding) {
                rtsBtn.setOnClickListener { v: View ->
                    toggle(v)
                }
                dtrBtn.setOnClickListener { v: View ->
                    toggle(v)
                }
            }
        }

        private fun toggle(v: View) {
            val control = v as ToggleButton
            if (!connected) {
                control.isChecked = !control.isChecked
                Toast.makeText(requireContext(), "not connected", Toast.LENGTH_SHORT).show()
                writeToFile("not connected")
                return
            }
            var ctrl = ""
            try {
                if (control == binding.rtsBtn) {
                    ctrl = "RTS"
                    usbSerialPort?.rts = control.isChecked
                }
                if (control == binding.dtrBtn) {
                    ctrl = "DTR"
                    usbSerialPort?.dtr = control.isChecked
                }
            } catch (e: IOException) {
                status("set" + ctrl + "() failed: " + e.message)
                writeToFile("set" + ctrl + "() failed: " + e.message)
            }
        }

        private fun run() {
            if (!connected) return
            try {
                with(binding) {
                    val controlLines: EnumSet<ControlLine>? = usbSerialPort?.controlLines
                    rtsBtn.isChecked = controlLines.isContains(ControlLine.RTS)
                    ctsBtn.isChecked = controlLines.isContains(ControlLine.CTS)
                    dtrBtn.isChecked = controlLines.isContains(ControlLine.DTR)
                    dsrBtn.isChecked = controlLines.isContains(ControlLine.DSR)
                    cdBtn.isChecked = controlLines.isContains(ControlLine.CD)
                    riBtn.isChecked = controlLines.isContains(ControlLine.RI)
                    mainLooper?.postDelayed(runnable, REFRESH_INTERVAL)
                }
            } catch (e: IOException) {
                status("getControlLines() failed: " + e.message + " -> stopped control line refresh")
                writeToFile("getControlLines() failed: " + e.message + " -> stopped control line refresh")
            }
        }

        fun start() {
            if (!connected) return
            try {
                with(binding) {
                    val controlLines: EnumSet<ControlLine>? = usbSerialPort?.supportedControlLines
                    if (!controlLines.isContains(ControlLine.RTS)) rtsBtn.visibility =
                        View.INVISIBLE
                    if (!controlLines.isContains(ControlLine.CTS)) ctsBtn.visibility =
                        View.INVISIBLE
                    if (!controlLines.isContains(ControlLine.DTR)) dtrBtn.visibility =
                        View.INVISIBLE
                    if (!controlLines.isContains(ControlLine.DSR)) dsrBtn.visibility =
                        View.INVISIBLE
                    if (!controlLines.isContains(ControlLine.CD)) cdBtn.visibility = View.INVISIBLE
                    if (!controlLines.isContains(ControlLine.RI)) riBtn.visibility = View.INVISIBLE
                    run()
                }
            } catch (e: IOException) {
                Toast.makeText(
                    requireContext(),
                    "getSupportedControlLines() failed: " + e.message,
                    Toast.LENGTH_SHORT
                ).show()
                writeToFile("getSupportedControlLines() failed: " + e.message)
            }
        }

        fun stop() {
            mainLooper?.removeCallbacks(runnable)
            with(binding) {
                rtsBtn.isChecked = false
                ctsBtn.isChecked = false
                dtrBtn.isChecked = false
                dsrBtn.isChecked = false
                cdBtn.isChecked = false
                riBtn.isChecked = false
            }

        }
    }

    companion object {
        const val INTENT_ACTION_GRANT_USB: String = "$APPLICATION_ID.GRANT_USB"
        const val WRITE_WAIT_MILLIS = 2000
        const val READ_WAIT_MILLIS = 2000
        const val REFRESH_INTERVAL = 200L // msec
    }
}