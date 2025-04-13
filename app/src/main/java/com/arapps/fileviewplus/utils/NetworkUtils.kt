package com.arapps.fileviewplus.utils

import android.content.Context



fun isOnWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}
fun getLocalIpAddress(): String {
    return try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    return addr.hostAddress ?: "localhost"
                }
            }
        }
        "localhost"
    } catch (_: Exception) {
        "localhost"
    }
}