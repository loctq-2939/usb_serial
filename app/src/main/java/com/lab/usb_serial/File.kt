package com.lab.usb_serial

import android.annotation.SuppressLint
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

@SuppressLint("SimpleDateFormat")
fun writeToFile(content: String, fileName: String = "log.txt") {
    val newDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    try {
        if (!newDir.exists()) {
            newDir.mkdir()
        }
        val file = File(newDir, fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        val out = PrintWriter(FileOutputStream(file, true))
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm:ss")
            val currentDate = sdf.format(Date())
            out.use {
                it.println(
                    "$currentDate : ${Thread.currentThread().stackTrace.getOrNull(4) ?: ""}  -----  $content"
                )
            }
        } catch (e: IOException) {
            System.err.println(e)
        } finally {
            out.close()
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

