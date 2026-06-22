package com.puretech.dialer

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * Helpers around call-capable phone accounts (SIMs). Dual-SIM UI only appears
 * when there is more than one account; single-SIM devices see nothing.
 */
object CallingAccounts {

    fun list(context: Context): List<PhoneAccountHandle> {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return try {
            tm.callCapablePhoneAccounts ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun isMultiSim(context: Context): Boolean = list(context).size > 1

    fun label(context: Context, handle: PhoneAccountHandle): String {
        val tm = context.getSystemService(TelecomManager::class.java)
        return try {
            tm?.getPhoneAccount(handle)?.label?.toString()?.ifBlank { null } ?: handle.id
        } catch (e: Exception) {
            handle.id
        }
    }

    /** The user's chosen default SIM handle, if still present. */
    fun defaultHandle(context: Context): PhoneAccountHandle? {
        val id = Prefs.defaultAccountId(context) ?: return null
        return list(context).firstOrNull { it.id == id }
    }

    /** Friendly SIM label for a call-log PHONE_ACCOUNT_ID (only when multi-SIM). */
    fun labelForCallLogId(context: Context, phoneAccountId: String?): String? {
        if (phoneAccountId.isNullOrBlank() || !isMultiSim(context)) return null
        val handle = list(context).firstOrNull { it.id == phoneAccountId } ?: return null
        return label(context, handle)
    }
}
