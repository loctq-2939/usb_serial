package com.lab.usb_serial

import java.security.InvalidParameterException

object HexDump {
    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )

    @JvmOverloads
    fun dumpHexString(array: ByteArray, offset: Int = 0, length: Int = array.size): String {
        val result = StringBuilder()
        val line = ByteArray(8)
        var lineIndex = 0
        for (i in offset until offset + length) {
            if (lineIndex == line.size) {
                for (j in line.indices) {
                    if (line[j] > "".toByte() && line[j] < "~".toByte()) {
                        result.append(String(line, j, 1))
                    } else {
                        result.append(".")
                    }
                }
                result.append("\n")
                lineIndex = 0
            }
            val b = array[i]
            result.append(HEX_DIGITS[b.toInt() ushr 4 and 0x0F])
            result.append(HEX_DIGITS[b.toInt() and 0x0F])
            result.append(" ")
            line[lineIndex++] = b
        }
        for (i in 0 until line.size - lineIndex) {
            result.append("   ")
        }
        for (i in 0 until lineIndex) {
            if (line[i] > " ".toByte() && line[i] < "~".toByte()) {
                result.append(String(line, i, 1))
            } else {
                result.append(".")
            }
        }
        return result.toString()
    }

    fun toHexString(b: Byte): String {
        return toHexString(toByteArray(b))
    }

    @JvmOverloads
    fun toHexString(array: ByteArray, offset: Int = 0, length: Int = array.size): String {
        val buf = CharArray(length * 2)
        var bufIndex = 0
        for (i in offset until offset + length) {
            val b = array[i]
            buf[bufIndex++] = HEX_DIGITS[b.toInt() ushr 4 and 0x0F]
            buf[bufIndex++] = HEX_DIGITS[b.toInt() and 0x0F]
        }
        return String(buf)
    }

    fun toHexString(i: Int): String {
        return toHexString(toByteArray(i))
    }

    fun toHexString(i: Short): String {
        return toHexString(toByteArray(i))
    }

    fun toByteArray(b: Byte): ByteArray {
        val array = ByteArray(1)
        array[0] = b
        return array
    }

    fun toByteArray(i: Int): ByteArray {
        val array = ByteArray(4)
        array[3] = (i and 0xFF).toByte()
        array[2] = (i shr 8 and 0xFF).toByte()
        array[1] = (i shr 16 and 0xFF).toByte()
        array[0] = (i shr 24 and 0xFF).toByte()
        return array
    }

    fun toByteArray(i: Short): ByteArray {
        val array = ByteArray(2)
        array[1] = (i.toInt() and 0xFF).toByte()
        array[0] = (i.toInt() shr 8 and 0xFF).toByte()
        return array
    }

    private fun toByte(c: Char): Int {
        if (c in '0'..'9') return c - '0'
        if (c in 'A'..'F') return c - 'A' + 10
        if (c in 'a'..'f') return c - 'a' + 10
        throw InvalidParameterException("Invalid hex char '$c'")
    }

    fun hexStringToByteArray(hexString: String): ByteArray {
        val length = hexString.length
        val buffer = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            buffer[i / 2] = (toByte(hexString[i]) shl 4 or toByte(hexString[i + 1])).toByte()
            i += 2
        }
        return buffer
    }
}