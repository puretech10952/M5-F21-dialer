package com.puretech.dialer

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Lifts Android's hidden-API restrictions (P+) for this process so we can call
 * vendor framework methods by reflection — specifically MediaTek's
 * `InCallService.doMtkAction(Bundle)`, which the F21's stock dialer uses to
 * start/stop call recording (see [CallManager]).
 *
 * A plain double-reflection bootstrap fails on the F21's Android 11 because
 * `VMRuntime.setHiddenApiExemptions` is itself a stricter `core-platform-api`
 * member; LSPosed's HiddenApiBypass reaches it anyway. Exempting "L" (the
 * prefix of every class signature) unblocks all hidden members.
 */
object HiddenApi {
    fun unseal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (e: Throwable) {
            // Best-effort; on ROMs without enforcement this isn't needed.
        }
    }
}
