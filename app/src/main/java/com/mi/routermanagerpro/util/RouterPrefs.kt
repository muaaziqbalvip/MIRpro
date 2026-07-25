package com.mi.routermanagerpro.util

import android.content.Context

object RouterPrefs {
    private const val PREFS_NAME = "router_manager_prefs"
    private const val KEY_SAVED_IPS = "saved_router_ips"

    fun getSavedRouters(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SAVED_IPS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    fun saveRouter(context: Context, ip: String) {
        val current = getSavedRouters(context).toMutableList()
        if (!current.contains(ip)) {
            current.add(0, ip)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_IPS, current.joinToString(",")).apply()
    }

    fun removeRouter(context: Context, ip: String) {
        val current = getSavedRouters(context).toMutableList()
        current.remove(ip)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_IPS, current.joinToString(",")).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
