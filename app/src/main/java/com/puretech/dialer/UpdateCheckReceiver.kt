package com.puretech.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by [UpdateScheduler]'s alarm. Queries GitHub for the latest release off
 * the main thread and, if it is newer than the installed build (and we haven't
 * already notified about it), raises an "update available" notification.
 */
class UpdateCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateScheduler.ACTION_CHECK) return
        // Respect the setting even if a stale alarm somehow survives.
        if (Prefs.updateFrequency(context) == Prefs.UPDATE_MANUAL) return

        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val release = Updater.fetchLatest()
                val current = currentVersionName(app)
                if (release.apkUrl != null &&
                    Updater.isNewer(release.versionName, current) &&
                    release.tag != Prefs.lastNotifiedTag(app)
                ) {
                    UpdateNotifier.notifyUpdate(app, release)
                    Prefs.setLastNotifiedTag(app, release.tag)
                }
            } catch (_: Exception) {
                // Offline or rate-limited — try again next interval.
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }
}
