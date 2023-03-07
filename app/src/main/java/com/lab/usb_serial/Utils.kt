package com.lab.usb_serial

import java.util.EnumSet

fun <T : Enum<T>> EnumSet<T>?.isContains(element: T): Boolean {
    return this?.contains(element) ?: false
}