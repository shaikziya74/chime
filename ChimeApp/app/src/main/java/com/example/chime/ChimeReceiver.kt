package com.example.chime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        if (!prefs.enabled) return
        Scheduler.schedule(ctx) // line up the next one first

        val trigger = intent.getLongExtra(Scheduler.EXTRA_TRIGGER, System.currentTimeMillis())
        if (System.currentTimeMillis() - trigger > 5 * 60_000) return // stale, skip
        val c = Calendar.getInstance().apply { timeInMillis = trigger }
        val isHour = c.get(Calendar.MINUTE) == 0
        if (isHour && !prefs.hourly) return
        if (!isHour && !prefs.half) return
        if (prefs.inQuietHours(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))) return
        ChimeService.start(ctx, isHour)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Scheduler.sync(ctx) }
}
