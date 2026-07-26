package com.mi.routermanagerpro.util

import com.mi.routermanagerpro.network.HuaweiRouterClient

/**
 * Simple in-memory holder for the currently authenticated router session.
 * Not persisted across process death by design (session cookies expire anyway).
 */
object RouterSession {

    private var activeIp: String? = null
    private var activeClient: HuaweiRouterClient? = null

    fun setActiveSession(ip: String, client: HuaweiRouterClient) {
        activeIp = ip
        activeClient = client
    }

    fun getActiveClient(): HuaweiRouterClient? = activeClient

    fun getActiveIp(): String? = activeIp

    fun isLoggedIn(): Boolean = activeClient?.isLoggedIn() == true

    fun clear() {
        activeClient?.logout()
        activeClient = null
        activeIp = null
    }
}
