package com.saha.upidisplay

import android.content.Context
import android.content.pm.PackageManager

object Prefs {
    /** Payment apps watched by default, with the short tag shown on screen. */
    val KNOWN = linkedMapOf(
        "com.google.android.apps.nbu.paisa.merchant" to "GPay Biz",
        "com.google.android.apps.nbu.paisa.user" to "GPay",
        "com.phonepe.app" to "PhonePe",
        "com.phonepe.app.business" to "PhonePe Biz",
        "com.phonepe.merchant" to "PhonePe Biz",
        "com.paytm.business" to "Paytm Biz",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
    )

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun watched(c: Context): Set<String> =
        HashSet(sp(c).getStringSet("watched", null) ?: KNOWN.keys)

    fun setWatched(c: Context, set: Set<String>) =
        sp(c).edit().putStringSet("watched", HashSet(set)).apply()

    fun isWatched(c: Context, pkg: String) = pkg in watched(c)

    fun sendToDisplay(c: Context) = sp(c).getBoolean("send", true)
    fun setSendToDisplay(c: Context, v: Boolean) = sp(c).edit().putBoolean("send", v).apply()

    fun displayMode(c: Context) = sp(c).getBoolean("display", false)
    fun setDisplayMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("display", v).apply()

    fun displayIp(c: Context): String = sp(c).getString("ip", "") ?: ""
    fun setDisplayIp(c: Context, v: String) = sp(c).edit().putString("ip", v.trim()).apply()

    fun bringToFront(c: Context) = sp(c).getBoolean("front", true)
    fun setBringToFront(c: Context, v: Boolean) = sp(c).edit().putBoolean("front", v).apply()

    /** Small status lines shown on the main screen to make problems easy to spot. */
    fun setStatus(c: Context, key: String, v: String) = sp(c).edit().putString("st_$key", v).apply()
    fun status(c: Context, key: String): String = sp(c).getString("st_$key", "") ?: ""

    fun tagFor(c: Context, pkg: String): String {
        KNOWN[pkg]?.let { return it }
        return try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().take(10)
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').take(10)
        }
    }
}
