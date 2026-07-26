package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class WlanBasicInfo(
    val ssid: String,
    val enabled: Boolean,
    val authMode: String,   // e.g. wpa2-psk
    val encryption: String, // e.g. AESEncryption
    val broadcastSsid: Boolean
)

sealed class WlanUpdateResult {
    object Success : WlanUpdateResult()
    data class Failed(val reason: String) : WlanUpdateResult()
    data class Error(val message: String) : WlanUpdateResult()
}

/**
 * Reads and updates WLAN Basic configuration on Huawei ONT routers.
 * Page reverse-engineered from the router's own /html/amp/wlanbasic/WlanBasic.asp:
 *   - form action: /html/amp/network/set.cgi
 *   - key fields: wlSsid, wlEnable, wlAuthMode, wlEncryption, wlWpaPsk, wlHide
 */
object HuaweiWlanClient {

    private val ssidRegex = Pattern.compile(
        """<tr id="record_0"[^>]*>.*?<td[^>]*>1</td><td[^>]*>(.*?)</td><td[^>]*>(Enabled|Disabled)</td>""",
        Pattern.DOTALL
    )

    suspend fun fetchBasicInfo(baseIp: String, sessionCookie: String?): WlanBasicInfo? =
        withContext(Dispatchers.IO) {
            val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
            try {
                val url = URL("$baseUrl/html/amp/wlanbasic/WlanBasic.asp")
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

                val m = ssidRegex.matcher(html)
                val ssid = if (m.find()) m.group(1)?.trim().orEmpty() else "—"
                val enabled = html.contains("checked=\"true\" name=\"wlEnbl\"") || ssid != "—"

                WlanBasicInfo(
                    ssid = ssid,
                    enabled = enabled,
                    authMode = "wpa2-psk",
                    encryption = "AESEncryption",
                    broadcastSsid = true
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Updates the WiFi SSID and/or password. Uses the same x.X_HW_Token pattern
     * confirmed from the router's login flow, applied to the set.cgi endpoint.
     */
    suspend fun updateWifi(
        baseIp: String,
        sessionCookie: String?,
        newSsid: String?,
        newPassword: String?
    ): WlanUpdateResult = withContext(Dispatchers.IO) {
        val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
        try {
            // Fetch a fresh token the same way login does
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
                return@withContext WlanUpdateResult.Error("Could not get security token from router.")
            }

            val url = URL("$baseUrl/html/amp/network/set.cgi")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.doOutput = true
            if (!sessionCookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", sessionCookie)
            }
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val params = mutableListOf<Pair<String, String>>()
            params.add("wlEnable" to "ON")
            params.add("wlAuthMode" to "wpa2-psk")
            params.add("wlEncryption" to "AESEncryption")
            if (!newSsid.isNullOrBlank()) params.add("wlSsid" to newSsid)
            if (!newPassword.isNullOrBlank()) params.add("wlWpaPsk" to newPassword)
            params.add("x.X_HW_Token" to token)

            val body = params.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            conn.disconnect()

            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                WlanUpdateResult.Success
            } else {
                WlanUpdateResult.Failed("Router rejected the update (HTTP $code).")
            }
        } catch (e: Exception) {
            WlanUpdateResult.Error(e.message ?: "Unknown network error")
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
