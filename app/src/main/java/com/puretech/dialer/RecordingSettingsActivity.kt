package com.puretech.dialer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityRecordingSettingsBinding

/**
 * Explains where call recordings are saved and lets the user grant the
 * "All files access" permission needed to file them into Music/Call recordings.
 */
class RecordingSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.grant.setOnClickListener { CallRecordings.requestAllFilesAccess(this) }
    }

    override fun onResume() {
        super.onResume()
        val ok = CallRecordings.hasAllFilesAccess()
        binding.status.text = getString(
            if (ok) R.string.recording_access_on else R.string.recording_access_off
        )
        binding.grant.isEnabled = !ok
    }
}
