package com.example.chime

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("chime", Context.MODE_PRIVATE)

    var enabled: Boolean get() = sp.getBoolean("enabled", false); set(v) = sp.edit().putBoolean("enabled", v).apply()
    var hourly: Boolean get() = sp.getBoolean("hourly", true); set(v) = sp.edit().putBoolean("hourly", v).apply()
    var half: Boolean get() = sp.getBoolean("half", true); set(v) = sp.edit().putBoolean("half", v).apply()
    var hourUri: String? get() = sp.getString("hourUri", null); set(v) = sp.edit().putString("hourUri", v).apply()
    var halfUri: String? get() = sp.getString("halfUri", null); set(v) = sp.edit().putString("halfUri", v).apply()
    var volume: Int get() = sp.getInt("volume", 80); set(v) = sp.edit().putInt("volume", v).apply()
    var maxSec: Int get() = sp.getInt("maxSec", 10); set(v) = sp.edit().putInt("maxSec", v).apply()
    var alarmStream: Boolean get() = sp.getBoolean("alarmStream", true); set(v) = sp.edit().putBoolean("alarmStream", v).apply()
    var quiet: Boolean get() = sp.getBoolean("quiet", false); set(v) = sp.edit().putBoolean("quiet", v).apply()
    /** minutes since midnight */
    var quietStart: Int get() = sp.getInt("quietStart", 22 * 60); set(v) = sp.edit().putInt("quietStart", v).apply()
    var quietEnd: Int get() = sp.getInt("quietEnd", 7 * 60); set(v) = sp.edit().putInt("quietEnd", v).apply()

    fun inQuietHours(minuteOfDay: Int): Boolean {
        if (!quiet) return false
        val s = quietStart; val e = quietEnd
        return if (s == e) false else if (s < e) minuteOfDay in s until e else (minuteOfDay >= s || minuteOfDay < e)
    }
}
