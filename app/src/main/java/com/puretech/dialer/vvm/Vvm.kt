package com.puretech.dialer.vvm

import android.content.Context
import android.telephony.CarrierConfigManager

/**
 * Visual Voicemail (OMTP family) plumbing. The flow mirrors the AOSP Dialer:
 *
 *   1. We register a [VvmService] (an `android.telephony.VisualVoicemailService`).
 *   2. The platform calls us when cellular service is up; we set an SMS filter and
 *      send the carrier an OMTP "Activate" message.
 *   3. The carrier replies with a silent STATUS SMS carrying the IMAP host / port /
 *      username / password. We store those.
 *   4. We connect to the carrier IMAP server, download voicemails (audio + headers)
 *      into Android's [android.provider.VoicemailContract], and sync read/delete back.
 *
 * Works only when we are the default dialer and the carrier provisions OMTP VVM
 * (T-Mobile = CVVM, AT&T = OMTP, Verizon = VVM3). Reads the carrier's VVM config
 * from [CarrierConfigManager]; if it is blank, VVM is unsupported on that SIM.
 */

const val VVM_TYPE_OMTP = "vvm_type_omtp"
const val VVM_TYPE_CVVM = "vvm_type_cvvm"
const val VVM_TYPE_VVM3 = "vvm_type_vvm3"

/** Carrier VVM settings pulled from [CarrierConfigManager]. */
data class VvmConfig(
    val type: String,
    val destinationNumber: String,
    val port: Int,
    val clientPrefix: String,
    val sslEnabled: Boolean,
    val prefetch: Boolean
) {
    /** True when the carrier actually advertises an OMTP-family VVM service. */
    val isSupported: Boolean
        get() = type.isNotBlank() && destinationNumber.isNotBlank()

    companion object {
        fun read(context: Context): VvmConfig? {
            val ccm = context.getSystemService(CarrierConfigManager::class.java) ?: return null
            @Suppress("DEPRECATION")
            val b = ccm.config ?: return null
            val type = b.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING, "").orEmpty()
            val dest = b.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, "").orEmpty()
            val port = b.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0)
            val prefix = b.getString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "//VVM")
                ?.ifBlank { "//VVM" } ?: "//VVM"
            val ssl = b.getBoolean(CarrierConfigManager.KEY_VVM_SSL_ENABLED_BOOL, false)
            val prefetch = b.getBoolean(CarrierConfigManager.KEY_VVM_PREFETCH_BOOL, true)
            return VvmConfig(type, dest, port, prefix, ssl, prefetch)
        }
    }
}

/** IMAP credentials the carrier hands us in the OMTP STATUS message. */
data class VvmCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val sslEnabled: Boolean
) {
    val isComplete: Boolean
        get() = host.isNotBlank() && port > 0 && username.isNotBlank() && password.isNotBlank()
}

/** One voicemail as fetched from IMAP, before it goes into VoicemailContract. */
data class VvmMessage(
    val uid: String,
    val sender: String,
    val dateMillis: Long,
    val durationSec: Long,
    val mimeType: String,
    val audio: ByteArray
)

/** Persisted OMTP provisioning state (IMAP creds) for the line. Device-local. */
object VvmPrefs {
    private const val FILE = "m5_vvm_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveCredentials(c: Context, cr: VvmCredentials) {
        sp(c).edit()
            .putString("host", cr.host)
            .putInt("port", cr.port)
            .putString("user", cr.username)
            .putString("pass", cr.password)
            .putBoolean("ssl", cr.sslEnabled)
            .apply()
    }

    fun credentials(c: Context): VvmCredentials? {
        val s = sp(c)
        val host = s.getString("host", null) ?: return null
        return VvmCredentials(
            host = host,
            port = s.getInt("port", 0),
            username = s.getString("user", "").orEmpty(),
            password = s.getString("pass", "").orEmpty(),
            sslEnabled = s.getBoolean("ssl", false)
        ).takeIf { it.isComplete }
    }

    fun clear(c: Context) = sp(c).edit().clear().apply()

    /** Whether the user has turned the VVM feature on (off by default — opt-in). */
    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("enabled", on).apply()
}
