package com.puretech.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

/**
 * Watches the carrier message-waiting indicator (MWI) and posts/clears a
 * voicemail notification. Best-effort: it only runs while our process is alive
 * and requires READ_PHONE_STATE. Registered once, lazily, when permission holds.
 */
object VoicemailMonitor {

    private var registered = false

    @Suppress("DEPRECATION")
    fun start(context: Context) {
        if (registered) return
        val app = context.applicationContext
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        val listener = object : PhoneStateListener() {
            override fun onMessageWaitingIndicatorChanged(mwi: Boolean) {
                if (mwi) VoicemailNotifier.show(app) else VoicemailNotifier.cancel(app)
            }
        }
        try {
            tm.listen(listener, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR)
            registered = true
        } catch (_: Exception) {
        }
    }
}
