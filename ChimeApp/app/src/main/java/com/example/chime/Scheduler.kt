package com.example.chime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    const val EXTRA_TRIGGER = "trigger"

    fun nextBoundary(now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.get(Calendar.MINUTE) < 30) c.set(Calendar.MINUTE, 30)
        else { c.set(Calendar.MINUTE, 0); c.add(Calendar.HOUR_OF_DAY, 1) }
        return c.timeInMillis
    }

    private fun pending(ctx: Context, trigger: Long): PendingIntent {
        val i = Intent(ctx, ChimeReceiver::class.java).putExtra(EXTRA_TRIGGER, trigger)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val t = nextBoundary()
        val pi = pending(ctx, t)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, 0))
    }

    fun sync(ctx: Context) { if (Prefs(ctx).enabled) schedule(ctx) else cancel(ctx) }
}
