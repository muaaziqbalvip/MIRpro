package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class MacFilterEntry(
    val ssidIndex: String,
    val mac: String
)

data class MacFilterStatus(
    val enabled: Boolean,
    val isWhitelist: Boolean,
    val entries: List<MacFilterEntry>
)

sealed class MacFilterActionResult {
    object Success : MacFilterActionResult()
    data class Failed(val reason: String) : MacFilterActionResult()
    data class Error(val message: String) : MacFilterActionResult()
}

/**
 * Blocks/unblocks WiFi devices via Huawei's WLAN MAC Address Filtering page.
 * Confirmed directly from the router's own pages:
 *   - Read:  GET  /html/bbsp/wlanmacfilter/wlanmacfilter.asp
 *   - Write: GET  /html/bbsp/wlanmacfilter/set.cgi
 *            ?x=InternetGatewayDevice.X_HW_Security.WLANMacFilter.1
 *            &RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp
 *            + form fields as query params: x.WlanMacFilterRight (ON/OFF),
 *              x.WlanMacFilterPolicy (0=Blacklist/1=Whitelist),
 *              x.SSIDName (SSID-1), x.SourceMACAddress (AA:BB:CC:DD:EE:FF),
 *              x.X_HW_Token (security token, same pattern as login)
 *
 * "Block" a device = add its MAC to the list while filter is enabled in
 * Blacklist mode. "Unblock" = remove its MAC from the list.
 */
object HuaweiMacFilterClient {

    private const val OBJECT_PATH = "InternetGatewayDevice.X_HW_Security.WLANMacFilter.1"
    private const val ASP_PATH = "html/bbsp/wlanmacfilter/wlanmacfilter.asp"

    private val entryRegex = Pattern.compile(
        """<td class="" id="WMacfilterConfigList_(\d+)_1"[^>]*>(.*?)</td><td class="" id="WMacfilterConfigList_\d+_2"[^>]*>([0-9a-fA-F:]+)</td>""",
        Pattern.DOTALL
    )
    private val enabledRegex = Pattern.compile(
        """id="EnableMacFilter"[^>]*type="checkbox"[^>]*checked""", Pattern.DOTALL
    )
    private val policyRegex = Pattern.compile(
        """<option id="2" value="1"[^>]*selected""", Pattern.DOTALL
    )

    suspend fun fetchStatus(baseIp: String, sessionCookie: String?): MacFilterStatus? =
        withContext(Dispatchers.IO) {
            val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
            try {
                val url = URL("$baseUrl/$ASP_PATH")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                if (!sessionCookie.isNullOrBlank()) {
                    conn.setRequestProperty("Cookie", sessionCookie)
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext null
                }
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val entries = mutableListOf<MacFilterEntry>()
                val m = entryRegex.matcher(html)
                while (m.find()) {
                    val ssid = m.group(2)?.trim().orEmpty()
                    val mac = m.group(3)?.trim().orEmpty()
                    if (mac.isNotBlank()) entries.add(MacFilterEntry(ssid.ifBlank { "1" }, mac))
                }

                MacFilterStatus(
                    enabled = enabledRegex.matcher(html).find(),
                    isWhitelist = policyRegex.matcher(html).find(),
                    entries = entries
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Blocks a device: ensures the filter is enabled in Blacklist mode and
     * adds the given MAC address to the list.
     */
    suspend fun blockDevice(baseIp: String, sessionCookie: String?, mac: String): MacFilterActionResult =
        applyChange(
            baseIp, sessionCookie,
            extraParams = listOf(
                "x.WlanMacFilterRight" to "1",
                "x.WlanMacFilterPolicy" to "0", // Blacklist
                "x.SSIDName" to "SSID-1",
                "x.SourceMACAddress" to mac
            )
        )

    /**
     * Unblocks a device: this removes it from the blacklist by re-submitting
     * without that MAC. Huawei's UI does this via a Delete checkbox + Delete
     * button rather than set.cgi directly; some firmwares also support
     * deleting via set.cgi with an empty SourceMACAddress for that record.
     * We attempt the direct set.cgi delete-style call here.
     */
    suspend fun unblockDevice(baseIp: String, sessionCookie: String?, mac: String): MacFilterActionResult =
        applyChange(
            baseIp, sessionCookie,
            extraParams = listOf(
                "x.SourceMACAddress" to mac,
                "x.Delete" to "1"
            )
        )

    suspend fun setEnabled(baseIp: String, sessionCookie: String?, enabled: Boolean, whitelist: Boolean): MacFilterActionResult =
        applyChange(
            baseIp, sessionCookie,
            extraParams = listOf(
                "x.WlanMacFilterRight" to if (enabled) "1" else "0",
                "x.WlanMacFilterPolicy" to if (whitelist) "1" else "0"
            )
        )

    private suspend fun applyChange(
        baseIp: String,
        sessionCookie: String?,
        extraParams: List<Pair<String, String>>
    ): MacFilterActionResult = withContext(Dispatchers.IO) {
        val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
        try {
            // fresh token, same pattern as login/WLAN save
            val tokenUrl = URL("$baseUrl/asp/GetRandCount.asp")
            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
            tokenConn.requestMethod = "POST"
            tokenConn.connectTimeout = 6000
            tokenConn.readTimeout = 6000
            if (!sessionCookie.isNullOrBlank()) {
                tokenConn.setRequestProperty("Cookie", sessionCookie)
            }
            tokenConn.doOutput = true
            tokenConn.outputStream.use { it.write(ByteArray(0)) }
            val token = tokenConn.inputStream.bufferedReader().use { it.readText() }.trim().trim('"')
            tokenConn.disconnect()

            if (token.isBlank()) {
                return@withContext MacFilterActionResult.Error("Could not get security token from router.")
            }

            val query = buildString {
                append("x=").append(urlEncode(OBJECT_PATH))
                append("&RequestFile=").append(urlEncode(ASP_PATH))
                for ((k, v) in extraParams) {
                    append("&").append(urlEncode(k)).append("=").append(urlEncode(v))
                }
                append("&x.X_HW_Token=").append(urlEncode(token))
            }

            val url = URL("$baseUrl/html/bbsp/wlanmacfilter/set.cgi?$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            if (!sessionCookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", sessionCookie)
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                MacFilterActionResult.Success
            } else {
                MacFilterActionResult.Failed("Router rejected the change (HTTP $code).")
            }
        } catch (e: Exception) {
            MacFilterActionResult.Error(e.message ?: "Unknown network error")
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
