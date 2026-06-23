package com.puretech.dialer.vvm

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.telephony.VisualVoicemailSms
import android.telephony.VisualVoicemailSmsFilterSettings
import android.util.Log

/**
 * Drives OMTP visual voicemail: turns the feature on (set SMS filter + send the
 * carrier an Activate message), handles the carrier's STATUS/SYNC replies, and
 * syncs the IMAP inbox into [VvmStore]. Network work must run off the main thread.
 */
object VvmSync {

    private const val TAG = "M5Vvm"

    // OMTP client identity used in the Activate/Status messages.
    private const val PROTOCOL_VERSION = "12"
    private const val CLIENT_TYPE = "Google"

    /** Turn VVM on for the default subscription: register the SMS filter and ask
     *  the carrier to activate. Returns false if unsupported or not permitted. */
    fun enable(context: Context): Boolean {
        val cfg = VvmConfig.read(context) ?: return false
        if (!cfg.isSupported) return false
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        try {
            tm.setVisualVoicemailSmsFilterSettings(
                VisualVoicemailSmsFilterSettings.Builder()
                    .setClientPrefix(cfg.clientPrefix)
                    .build()
            )
            sendActivate(tm, cfg)
            VvmPrefs.setEnabled(context, true)
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "enable denied (need to be default dialer): ${e.message}")
            return false
        } catch (e: Exception) {
            Log.w(TAG, "enable failed: ${e.message}")
            return false
        }
    }

    /** Turn VVM off: clear the SMS filter and forget stored credentials. */
    fun disable(context: Context) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        try {
            tm?.setVisualVoicemailSmsFilterSettings(null)
        } catch (_: Exception) {
        }
        VvmPrefs.setEnabled(context, false)
        VvmPrefs.clear(context)
    }

    private fun sendActivate(tm: TelephonyManager, cfg: VvmConfig) {
        val text = when (cfg.type) {
            // Verizon VVM3 provisions through a STATUS request first.
            VVM_TYPE_VVM3 -> "STATUS"
            else -> "Activate:pv=$PROTOCOL_VERSION;ct=$CLIENT_TYPE"
        }
        tm.sendVisualVoicemailSms(cfg.destinationNumber, cfg.port, text, null)
    }

    /** Handle a VVM SMS delivered to our VisualVoicemailService. */
    fun onSms(context: Context, sms: VisualVoicemailSms) {
        val event = sms.prefix ?: return
        val fields = sms.fields ?: Bundle.EMPTY
        Log.d(TAG, "VVM SMS event=$event fields=$fields")
        when (event.uppercase()) {
            "STATUS" -> {
                if (storeCredentials(context, fields)) sync(context)
            }
            "SYNC" -> sync(context)
        }
    }

    /** Pull IMAP host/user/password out of an OMTP STATUS message. */
    private fun storeCredentials(context: Context, f: Bundle): Boolean {
        val provisioning = f.getString("st").orEmpty()
        // "N"/"U"/"B" => not ready; "R" (ready) or creds present => good.
        val server = f.getString("srv").orEmpty()
        val host = server.substringBefore(':').trim()
        val portFromSrv = server.substringAfter(':', "").toIntOrNull()
        val imapPort = f.getString("ipt")?.toIntOrNull() ?: portFromSrv ?: 143
        val user = f.getString("u").orEmpty()
        val pass = f.getString("pw").orEmpty()
        val cfg = VvmConfig.read(context)
        val cr = VvmCredentials(host, imapPort, user, pass, cfg?.sslEnabled ?: false)
        if (!cr.isComplete) {
            Log.w(TAG, "STATUS not provisioned (st=$provisioning, host=$host)")
            return false
        }
        VvmPrefs.saveCredentials(context, cr)
        return true
    }

    /** Reconcile the carrier IMAP inbox into the local voicemail store. */
    fun sync(context: Context): Boolean {
        val cr = VvmPrefs.credentials(context) ?: return false
        val client = ImapClient(cr)
        return try {
            client.connect(); client.login(); client.selectInbox()
            val server = client.listUids()
            val local = VvmStore.existingUids(context)
            var added = 0
            for (uid in server) {
                if (uid in local) continue
                val msg = client.fetchMessage(uid) ?: continue
                if (VvmStore.insert(context, msg)) added++
            }
            Log.d(TAG, "sync complete: ${server.size} on server, $added new")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            false
        } finally {
            client.close()
        }
    }

    /** Delete a voicemail locally and from the carrier server. */
    fun deleteVoicemail(context: Context, id: Long) {
        val uid = VvmStore.sourceData(context, id)
        VvmStore.delete(context, id)
        if (uid != null) {
            val cr = VvmPrefs.credentials(context) ?: return
            val client = ImapClient(cr)
            try {
                client.connect(); client.login(); client.selectInbox()
                client.delete(uid)
            } catch (_: Exception) {
            } finally {
                client.close()
            }
        }
    }
}
