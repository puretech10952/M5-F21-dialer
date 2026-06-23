package com.puretech.dialer

import android.content.ContentUris
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.VoicemailContract.Voicemails
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.puretech.dialer.databinding.ActivityVoicemailBinding
import com.puretech.dialer.vvm.VvmConfig
import com.puretech.dialer.vvm.VvmPrefs
import com.puretech.dialer.vvm.VvmStore
import com.puretech.dialer.vvm.VvmSync

/**
 * Visual-voicemail screen: turn the feature on, then list voicemails the carrier
 * pushed to us (downloaded over IMAP by [VvmSync]) with tap-to-play, call back
 * and delete. Requires being the default dialer on a carrier that offers OMTP VVM.
 */
class VoicemailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoicemailBinding
    private lateinit var adapter: VoicemailAdapter
    private var player: MediaPlayer? = null
    private var playingId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoicemailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.refresh.setOnClickListener { syncNow() }

        adapter = VoicemailAdapter(
            onPlay = { togglePlay(it) },
            onCall = { Dialer.place(this, Dialer.normalize(this, it.number)) },
            onDelete = { confirmDelete(it) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.enableSwitch.setOnClickListener { onToggleEnable() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        load()
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    // --- Enable / status ------------------------------------------------------

    private fun refreshUi() {
        val cfg = VvmConfig.read(this)
        val supported = cfg?.isSupported == true
        val enabled = VvmPrefs.enabled(this)
        binding.enableSwitch.isChecked = enabled
        binding.enableSwitch.isEnabled = supported

        binding.status.text = when {
            !supported -> getString(R.string.vvm_unsupported)
            enabled && VvmPrefs.credentials(this) == null -> getString(R.string.vvm_provisioning)
            enabled -> getString(R.string.vvm_enabled_sub)
            else -> getString(R.string.vvm_enable_sub)
        }
    }

    private fun onToggleEnable() {
        val turnOn = binding.enableSwitch.isChecked
        if (turnOn) {
            Thread {
                val ok = VvmSync.enable(applicationContext)
                runOnUiThread {
                    if (!ok) {
                        binding.enableSwitch.isChecked = false
                        Toast.makeText(this, R.string.vvm_enable_failed, Toast.LENGTH_LONG).show()
                    }
                    refreshUi()
                }
            }.start()
        } else {
            Thread { VvmSync.disable(applicationContext) }.start()
            refreshUi()
        }
    }

    private fun syncNow() {
        if (!VvmPrefs.enabled(this)) {
            Toast.makeText(this, R.string.vvm_enable_first, Toast.LENGTH_SHORT).show()
            return
        }
        binding.refresh.isEnabled = false
        Thread {
            VvmSync.sync(applicationContext)
            runOnUiThread {
                binding.refresh.isEnabled = true
                load()
            }
        }.start()
    }

    // --- List -----------------------------------------------------------------

    private fun load() {
        Thread {
            val items = query()
            runOnUiThread {
                adapter.submit(items)
                binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun query(): List<VoicemailItem> {
        val out = ArrayList<VoicemailItem>()
        val uri = Voicemails.buildSourceUri(packageName)
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE,
                    Voicemails.DURATION, Voicemails.IS_READ, Voicemails.HAS_CONTENT
                ),
                null, null, "${Voicemails.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(1).orEmpty()
                    out.add(
                        VoicemailItem(
                            id = c.getLong(0),
                            number = number,
                            displayName = ContactsRepository.displayName(this, number),
                            dateMillis = c.getLong(2),
                            durationSec = c.getLong(3),
                            isRead = c.getInt(4) != 0,
                            hasContent = c.getInt(5) != 0
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    // --- Playback -------------------------------------------------------------

    private fun togglePlay(item: VoicemailItem) {
        if (!item.hasContent) {
            Toast.makeText(this, R.string.vvm_no_audio, Toast.LENGTH_SHORT).show()
            return
        }
        if (playingId == item.id) { stopPlayback(); return }
        stopPlayback()
        try {
            val uri = ContentUris.withAppendedId(Voicemails.CONTENT_URI, item.id)
            player = MediaPlayer().apply {
                setDataSource(this@VoicemailActivity, uri)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playingId = item.id
            adapter.setPlaying(item.id)
            if (!item.isRead) markRead(item.id)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.vvm_play_failed, Toast.LENGTH_SHORT).show()
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingId = -1L
        adapter.setPlaying(-1L)
    }

    private fun markRead(id: Long) {
        Thread { VvmStore.markRead(applicationContext, id) }.start()
    }

    private fun confirmDelete(item: VoicemailItem) {
        AlertDialog.Builder(this)
            .setMessage(R.string.vvm_delete_confirm)
            .setPositiveButton(R.string.vvm_delete) { _, _ ->
                if (playingId == item.id) stopPlayback()
                Thread {
                    VvmSync.deleteVoicemail(applicationContext, item.id)
                    runOnUiThread { load() }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
