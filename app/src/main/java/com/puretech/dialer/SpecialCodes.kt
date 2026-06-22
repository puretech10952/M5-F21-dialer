package com.puretech.dialer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Dialer "MMI" short-codes that the dialer handles itself instead of placing a
 * call — mirroring the stock dialer's SpecialCharSequenceMgr:
 *   • *#06#            → show the device IMEI/MEID.
 *   • *#*#<code>#*#*   → fire the hidden "secret code" broadcast.
 *
 * On Android 10+ reading the IMEI needs a privileged permission we don't hold,
 * so when the direct read is blocked we fall back to the system Device-info
 * screen, which shows the IMEI.
 */
object SpecialCodes {

    // Same shape the stock dialer matches: *#*#<code>#*#* (code may contain
    // non-digit chars on some engineering menus, so accept anything but '#').
    private val SECRET = Regex("""\*#\*#([^#]+)#\*#\*""")

    /** @return true if [input] was a special code and was handled (don't dial). */
    fun handle(activity: Activity, input: String): Boolean {
        val s = input.filter { it.isDigit() || it in "+*#,;" }
        if (s == "*#06#") {
            showDeviceIds(activity)
            return true
        }
        SECRET.matchEntire(s)?.let { m ->
            dispatchSecretCode(activity, m.groupValues[1])
            return true
        }
        return false
    }

    /**
     * Fire an engineering "secret code" the way every stock dialer does
     * (mirrors AOSP's TelephonyManagerCompat.handleSecretCode):
     *   1. Ask the telephony service to broadcast it via sendDialerSpecialCode().
     *      Since Android 8 the SECRET_CODE broadcast is protected — only the
     *      system may send it — so this is the ONLY path that works, and it's
     *      gated on being the default dialer (hence we need the DIALER role).
     *   2. If that throws (older builds / not default), fall back to sending the
     *      legacy SECRET_CODE broadcast directly.
     */
    private fun dispatchSecretCode(context: Context, code: String) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        try {
            tm?.sendDialerSpecialCode(code)
            return
        } catch (_: SecurityException) {
            // Not the default dialer — fall through to the legacy broadcast.
        } catch (_: Exception) {
            // NPE on some builds when telephony is unavailable — fall through.
        }
        try {
            context.sendBroadcast(
                Intent(
                    "android.provider.Telephony.SECRET_CODE",
                    Uri.parse("android_secret_code://$code")
                )
            )
        } catch (_: Exception) {
            // Protected broadcast on newer Android — best effort only.
        }
    }

    private fun showDeviceIds(activity: Activity) {
        val ids = readDeviceIds(activity)
        if (ids.isNotEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.imei_title)
                .setMessage(ids.joinToString("\n"))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.imei_copy) { _, _ ->
                    copy(activity, ids.joinToString("\n"))
                    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            // No privileged access to the identifiers — send the user to the
            // system Device-info screen, which displays the IMEI.
            try {
                activity.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                Toast.makeText(activity, R.string.imei_in_settings, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
    }

    /** Best-effort per-SIM IMEI/MEID; empty when the OS blocks the read. */
    private fun readDeviceIds(context: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return out
            val slots = try { tm.phoneCount } catch (_: Throwable) { 1 }.coerceAtLeast(1)
            for (slot in 0 until slots) {
                val imei = runCatching { tm.getImei(slot) }.getOrNull()?.ifBlank { null }
                val meid = runCatching { tm.getMeid(slot) }.getOrNull()?.ifBlank { null }
                val prefix = if (slots > 1) " (SIM ${slot + 1})" else ""
                when {
                    imei != null -> out.add("IMEI$prefix: $imei")
                    meid != null -> out.add("MEID$prefix: $meid")
                }
            }
            if (out.isEmpty()) {
                // Single-id fallback (older API path).
                @Suppress("DEPRECATION", "HardwareIds")
                val imei = runCatching { tm.imei }.getOrNull()?.ifBlank { null }
                if (imei != null) out.add("IMEI: $imei")
            }
        } catch (_: Throwable) {
        }
        return out.distinct()
    }

    private fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IMEI", text))
    }
}
