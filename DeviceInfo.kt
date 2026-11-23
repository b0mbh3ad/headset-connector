package com.headsetconnector.agent

import android.content.Context
import android.os.BatteryManager
import android.os.Build

object DeviceInfo {

    fun isHeadset(): Boolean {
        val text = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.PRODUCT}".lowercase()
        return text.contains("pico") ||
            text.contains("quest") ||
            text.contains("oculus") ||
            text.contains("meta")
    }

    fun model(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun battery(context: Context): Int = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

    fun charging(context: Context): Boolean = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)
}
