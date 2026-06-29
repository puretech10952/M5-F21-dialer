package com.puretech.dialer

import android.content.Context
import android.os.PowerManager

/**
 * Holds the PROXIMITY_SCREEN_OFF_WAKE_LOCK for the WHOLE duration of an earpiece
 * call — not just while the in-call screen is foreground. Because the wake lock
 * is a system-level lock owned by [CallService] (which lives for the entire
 * call), the screen blanks when the phone is held to the ear no matter what app
 * is on screen, and lights back up when moved away.
 *
 * Driven by [CallManager] state: active call + earpiece route → acquire,
 * otherwise → release.
 */
object ProximityController : CallManager.Listener {

    private var wakeLock: PowerManager.WakeLock? = null
    private var appContext: Context? = null

    /** Start watching call state (called when the first call is added). */
    fun attach(context: Context) {
        appContext = context.applicationContext
        CallManager.registerListener(this)   // also fires onCallChanged() immediately
    }

    /** Stop watching and release the screen (called when the last call ends). */
    fun detach() {
        CallManager.unregisterListener(this)
        release()
    }

    override fun onCallChanged() {
        val shouldBlank = CallManager.activeCall() != null && CallManager.isOnEarpiece()
        if (shouldBlank) acquire() else release()
    }

    private fun acquire() {
        val ctx = appContext ?: return
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return
        if (wakeLock == null) {
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            wakeLock = pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "m5dialer:proximity"
            )
        }
        if (wakeLock?.isHeld == false) wakeLock?.acquire(60 * 60 * 1000L)
    }

    private fun release() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
