package com.puretech.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/**
 * The official hook that makes us own missed-call notifications.
 *
 * When we are the default dialer AND declare a receiver for
 * [TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION], the Telecom framework
 * stops posting its own (un-mutable) missed-call notification and instead
 * broadcasts here, handing us the caller's number and the running missed count.
 * We then post our own notification — so there is exactly one, and it opens our
 * dialer. This is why other dialers suppress the system's notification.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return

        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)

        // count == 0 is Telecom telling us to clear missed-call notifications.
        if (count <= 0) {
            MissedCallNotifier.cancelAll(context)
            return
        }
        MissedCallNotifier.show(context, number ?: "")
    }
}
