package com.mi.routermanagerpro.util

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.NetworkInterface

data class WifiInfoSnapshot(
    val isConnected: Boolean,
    val ssid: String,
    val gatewayIp: String,
    val deviceIp: String,
    val linkSpeedMbps: Int,
    val signalLevelPercent: Int,
    val frequencyMhz: Int
)

object NetworkUtils {

    fun getWifiSnapshot(context: Context): WifiInfoSnapshot {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        val dhcp = wifiManager.dhcpInfo

        val connected = info != null && info.networkId != -1
        var ssid = info?.ssid ?: "<unknown>"
        ssid = ssid.trim('"')
        if (ssid == "<unknown ssid>" || ssid.isBlank()) ssid = "—"

        val gatewayIp = if (dhcp != null && dhcp.gateway != 0) {
            intToIp(dhcp.gateway)
        } else "—"

        val deviceIp = if (info != null && info.ipAddress != 0) {
            intToIp(info.ipAddress)
        } else Formatter.formatIpAddress(dhcp?.ipAddress ?: 0)

        val linkSpeed = info?.linkSpeed ?: 0
        val rssi = info?.rssi ?: -100
        val level = WifiManager.calculateSignalLevel(rssi, 100)
        val freq = try { info?.frequency ?: 0 } catch (e: Exception) { 0 }

        return WifiInfoSnapshot(
            isConnected = connected,
            ssid = ssid,
            gatewayIp = gatewayIp,
            deviceIp = deviceIp,
            linkSpeedMbps = linkSpeed,
            signalLevelPercent = level,
            frequencyMhz = freq
        )
    }

    private fun intToIp(addr: Int): String {
        return "${addr and 0xFF}.${(addr shr 8) and 0xFF}.${(addr shr 16) and 0xFF}.${(addr shr 24) and 0xFF}"
    }

    /** Guesses common default gateway IPs to try if system reports none */
    fun commonDefaultGateways(): List<String> = listOf(
        "192.168.100.1", // Huawei ONT/GPON default (e.g. HS8545M5)
        "192.168.1.1",
        "192.168.0.1",
        "192.168.8.1",
        "192.168.3.1"
    )

    fun localSubnetPrefix(deviceIp: String): String? {
        val parts = deviceIp.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}."
    }
}
