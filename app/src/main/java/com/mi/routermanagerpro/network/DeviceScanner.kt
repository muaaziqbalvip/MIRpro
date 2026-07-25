package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

data class ScannedDevice(
    val ip: String,
    val hostname: String,
    val macPrefix: String
)

object DeviceScanner {

    /**
     * Scans the /24 subnet the device is currently on by pinging each host.
     * Returns the list of hosts that responded.
     */
    suspend fun scanSubnet(subnetPrefix: String, timeoutMs: Int = 250): List<ScannedDevice> =
        withContext(Dispatchers.IO) {
            val deferredResults = (1..254).map { host ->
                async {
                    val ip = "$subnetPrefix$host"
                    try {
                        val addr = InetAddress.getByName(ip)
                        if (addr.isReachable(timeoutMs)) {
                            val hostname = try {
                                addr.canonicalHostName
                            } catch (e: Exception) {
                                ip
                            }
                            ScannedDevice(ip = ip, hostname = if (hostname == ip) "Device" else hostname, macPrefix = "")
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            deferredResults.awaitAll().filterNotNull()
        }
}
