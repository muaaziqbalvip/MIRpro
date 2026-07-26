package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class HuaweiDevice(
    val index: Int,
    val name: String,
    val port: String,       // e.g. "SSID1" for WiFi, or LAN port for wired
    val mac: String,
    val ip: String,
    val isOnline: Boolean,
    val connectionDuration: String
)

/**
 * Fetches and parses the connected-devices list from a Huawei ONT/router,
 * using the router's own internal endpoint:
 *   GET /html/bbsp/userdevinfo/userdevinfosmart.asp?type=wifidev  (WiFi devices)
 *   GET /html/bbsp/userdevinfo/userdevinfosmart.asp?type=landev   (wired devices)
 *
 * Parsing is regex-based against the server-rendered HTML table structure
 * (div ids: DivIpandMac_N, DivDevStatus_N, DivConnectTime_N, divDevName_N, DivDevPort_N)
 * confirmed directly from a real saved page of this router model.
 */
object HuaweiDeviceListFetcher {

    private val nameRegex = Pattern.compile(
        """<div id="divDevName_(\d+)">(.*?)</div>""", Pattern.DOTALL
    )
    private val portRegex = Pattern.compile(
        """<div id="DivDevPort_(\d+)">(.*?)</div>""", Pattern.DOTALL
    )
    private val ipMacRegex = Pattern.compile(
        """<div id="DivIpandMac_(\d+)">([0-9a-fA-F:]+)<br>([0-9.]*)<br>?</div>""", Pattern.DOTALL
    )
    private val statusRegex = Pattern.compile(
        """<div id="DivDevStatus_(\d+)">(.*?)</div>""", Pattern.DOTALL
    )
    private val durationRegex = Pattern.compile(
        """<div id="DivConnectTime_(\d+)">(.*?)</div>""", Pattern.DOTALL
    )

    suspend fun fetch(baseIp: String, sessionCookie: String?, type: String = "wifidev"): List<HuaweiDevice> =
        withContext(Dispatchers.IO) {
            val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
            try {
                val url = URL("$baseUrl/html/bbsp/userdevinfo/userdevinfosmart.asp?type=$type")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                if (!sessionCookie.isNullOrBlank()) {
                    conn.setRequestProperty("Cookie", sessionCookie)
                }

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext emptyList()
                }

                val html = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                parseDevices(html)
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun parseDevices(html: String): List<HuaweiDevice> {
        val names = mutableMapOf<Int, String>()
        val ports = mutableMapOf<Int, String>()
        val ipMacs = mutableMapOf<Int, Pair<String, String>>()
        val statuses = mutableMapOf<Int, Boolean>()
        val durations = mutableMapOf<Int, String>()

        var m = nameRegex.matcher(html)
        while (m.find()) {
            val idx = m.group(1)?.toIntOrNull() ?: continue
            names[idx] = m.group(2)?.trim().orEmpty()
        }

        m = portRegex.matcher(html)
        while (m.find()) {
            val idx = m.group(1)?.toIntOrNull() ?: continue
            ports[idx] = m.group(2)?.trim().orEmpty()
        }

        m = ipMacRegex.matcher(html)
        while (m.find()) {
            val idx = m.group(1)?.toIntOrNull() ?: continue
            val mac = m.group(2)?.trim().orEmpty()
            val ip = m.group(3)?.trim().orEmpty()
            ipMacs[idx] = mac to ip
        }

        m = statusRegex.matcher(html)
        while (m.find()) {
            val idx = m.group(1)?.toIntOrNull() ?: continue
            val statusText = m.group(2)?.trim().orEmpty()
            statuses[idx] = statusText.equals("Online", ignoreCase = true)
        }

        m = durationRegex.matcher(html)
        while (m.find()) {
            val idx = m.group(1)?.toIntOrNull() ?: continue
            durations[idx] = m.group(2)?.trim().orEmpty()
        }

        val indices = names.keys.sorted()
        return indices.map { idx ->
            val (mac, ip) = ipMacs[idx] ?: ("" to "")
            HuaweiDevice(
                index = idx,
                name = names[idx]?.ifBlank { "Unknown Device" } ?: "Unknown Device",
                port = ports[idx].orEmpty(),
                mac = mac,
                ip = ip,
                isOnline = statuses[idx] ?: false,
                connectionDuration = durations[idx]?.ifBlank { "--" } ?: "--"
            )
        }
    }
}
