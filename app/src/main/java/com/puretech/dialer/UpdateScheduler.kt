package com.puretech.dialer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules periodic background update checks with [AlarmManager] (no extra
 * dependency, no exact-alarm permission). The alarm fires [UpdateCheckReceiver],
 * which queries GitHub and notifies if a newer release is out. Inexact repeating
 * alarms are cleared on reboot, so [BootReceiver] re-arms this after a restart.
 */
object UpdateScheduler {

    private const val REQUEST = 91
    const val ACTION_CHECK = "com.puretech.dialer.action.CHECK_UPDATE"

    private fun intervalMs(frequency: Int): Long = when (frequency) {
        Prefs.UPDATE_DAILY -> AlarmManager.INTERVAL_DAY
        Prefs.UPDATE_WEEKLY -> 7 * AlarmManager.INTERVAL_DAY
        Prefs.UPDATE_MONTHLY -> 30 * AlarmManager.INTERVAL_DAY
        else -> 0L
    }

    /** Apply the user's current schedule: cancel any existing alarm, then (unless
     *  set to Manual) arm an inexact repeating alarm at the chosen interval. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)

        val interval = intervalMs(Prefs.updateFrequency(context))
        if (interval <= 0L) return

        val first = System.currentTimeMillis() + interval
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, interval, pi)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java).setAction(ACTION_CHECK)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
    }
}
