package com.puretech.dialer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityRedirectSettingsBinding

/**
 * Explains the Send-key redirect helper and links straight to the system
 * Accessibility toggle. We can't enable an accessibility service ourselves —
 * Android requires the user to do it — so this screen just deep-links there.
 */
class RedirectSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedirectSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRedirectSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.openAccessibility.setOnClickListener { openAccessibilitySettings() }
    }

    override fun onResume() {
        super.onResume()
        val on = DialerRedirectService.isEnabled(this)
        binding.status.text = getString(
            if (on) R.string.redirect_status_on else R.string.redirect_status_off
        )
    }

    /** Deep-link to this app's accessibility entry; fall back to the full list. */
    private fun openAccessibilitySettings() {
        val component = ComponentName(this, DialerRedirectService::class.java).flattenToString()
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val args = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) }
            intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args)
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    companion object {
        // Hidden but widely-supported keys to scroll/highlight a specific entry.
        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
