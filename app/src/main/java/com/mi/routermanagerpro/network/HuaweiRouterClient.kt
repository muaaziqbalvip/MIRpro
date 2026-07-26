package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.util.Base64

/**
 * Native HTTP client for Huawei EchoLife GPON ONT routers (e.g. HS8545M5).
 *
 * Protocol reverse-engineered directly from the router's own login.asp /
 * safelogin.js / RndSecurityFormat.js source:
 *   1. GET/POST /asp/GetRandCount.asp -> returns a one-time token string
 *   2. POST /login.cgi with UserName, PassWord (Base64-encoded plain password),
 *      Language, x.X_HW_Token
 *   3. Server responds with a Set-Cookie session header on success
 *
 * Default accounts on this device family:
 *   - "root"          (normal/user account)
 *   - "telecomadmin"  (admin account)
 */
sealed class HuaweiLoginResult {
    data class Success(val sessionCookie: String) : HuaweiLoginResult()
    data class Failed(val reason: String) : HuaweiLoginResult()
    data class Error(val message: String) : HuaweiLoginResult()
}

class HuaweiRouterClient(private val baseIp: String) {

    private val baseUrl = if (baseIp.startsWith("http")) baseIp else "http://$baseIp"
    private var sessionCookie: String? = null

    private fun base64Encode(input: String): String {
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Step 0: GET the login page first, exactly like a real browser would,
     * to capture any pre-session cookie the router issues before login.
     * Some Huawei firmwares validate the login token against this cookie.
     */
    private fun fetchPreLoginCookie(): String? {
        return try {
            val url = URL("$baseUrl/login.asp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            val cookies = conn.headerFields["Set-Cookie"]
            val combined = cookies?.joinToString("; ") { it.substringBefore(";") }
            conn.disconnect()
            combined
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Step 1: fetch the one-time login token from GetRandCount.asp
     */
    private fun fetchToken(preLoginCookie: String?): String? {
        return try {
            val url = URL("$baseUrl/asp/GetRandCount.asp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (!preLoginCookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", preLoginCookie)
            }
            conn.outputStream.use { it.write(ByteArray(0)) }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val token = reader.readText().trim().trim('"')
            reader.close()
            conn.disconnect()
            token.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Step 2: submit login.cgi with credentials + token.
     * Mirrors the exact field names used by the router's own LoginSubmit() JS function.
     */
    suspend fun login(username: String, password: String): HuaweiLoginResult =
        withContext(Dispatchers.IO) {
            val preLoginCookie = fetchPreLoginCookie()

            val token = fetchToken(preLoginCookie) ?: return@withContext HuaweiLoginResult.Error(
                "Could not reach router or fetch security token. Check the IP and that you're on the router's WiFi."
            )

            try {
                val url = URL("$baseUrl/login.cgi")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val cookieToSend = if (!preLoginCookie.isNullOrBlank())
                    preLoginCookie
                else
                    "Cookie=body:Language:english:id=-1"
                conn.setRequestProperty("Cookie", cookieToSend)

                val encodedPassword = base64Encode(password)
                val body = buildString {
                    append("UserName=").append(urlEncode(username))
                    append("&PassWord=").append(urlEncode(encodedPassword))
                    append("&Language=").append("english")
                    append("&x.X_HW_Token=").append(urlEncode(token))
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode

                // Combine ALL Set-Cookie headers (Huawei firmware often sets more than one:
                // a session cookie plus a language/body cookie) so later authenticated
                // requests (device list, WLAN settings, etc.) carry the full cookie set.
                val allCookies = conn.headerFields["Set-Cookie"]
                val combinedCookie = allCookies?.joinToString("; ") { it.substringBefore(";") }

                // Read body for diagnostics (works for both success/redirect and error responses)
                val responseBody = try {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader()?.use { it.readText() }?.take(300) ?: ""
                } catch (e: Exception) {
                    ""
                }

                val location = conn.getHeaderField("Location") ?: ""

                conn.disconnect()

                val redirectsToLogin = location.contains("login.asp", ignoreCase = true)
                val redirectsAway = (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_MOVED_PERM) && !redirectsToLogin

                if (!combinedCookie.isNullOrBlank()) {
                    sessionCookie = combinedCookie
                    HuaweiLoginResult.Success(sessionCookie!!)
                } else if (redirectsAway && !preLoginCookie.isNullOrBlank()) {
                    // No new cookie, but the router redirected us somewhere other than
                    // back to the login page — treat the pre-login session cookie as
                    // the now-authenticated session (some firmwares keep the same
                    // session id across login rather than issuing a fresh one).
                    sessionCookie = preLoginCookie
                    HuaweiLoginResult.Success(sessionCookie!!)
                } else if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM) {
                    // Redirect with no cookie is ambiguous on this firmware — it can mean
                    // either success (session set via a header our parsing missed) or
                    // failure (bounced back to login.asp). Surface it as a failure with
                    // diagnostic detail rather than silently assuming success.
                    HuaweiLoginResult.Failed(
                        "Router responded with a redirect but no session cookie (HTTP $code). Location: ${location.ifBlank { "none" }}"
                    )
                } else {
                    HuaweiLoginResult.Failed(
                        "Login rejected by router (HTTP $code). Check username/password. Response: ${responseBody.ifBlank { "(empty)" }}"
                    )
                }
            } catch (e: Exception) {
                HuaweiLoginResult.Error(e.message ?: "Unknown network error")
            }
        }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    fun isLoggedIn(): Boolean = sessionCookie != null

    fun getSessionCookie(): String? = sessionCookie

    fun logout() {
        sessionCookie = null
    }
}
