package com.puretech.dialer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivitySettingsBinding

/** Settings hub: theme, blocked numbers, quick responses, assisted dialing, SIMs. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowBlocked.setOnClickListener {
            startActivity(Intent(this, BlockedNumbersActivity::class.java))
        }
        binding.rowQuick.setOnClickListener {
            startActivity(Intent(this, QuickResponsesActivity::class.java))
        }
        binding.rowAssisted.setOnClickListener {
            startActivity(Intent(this, AssistedDialingActivity::class.java))
        }
        binding.rowAccounts.setOnClickListener {
            startActivity(Intent(this, CallingAccountsActivity::class.java))
        }
        binding.rowFloating.setOnClickListener {
            startActivity(Intent(this, KeypadSettingsActivity::class.java))
        }
        binding.rowRedirect.setOnClickListener {
            startActivity(Intent(this, RedirectSettingsActivity::class.java))
        }
        binding.rowKeepAlive.setOnClickListener {
            startActivity(Intent(this, KeepAliveSettingsActivity::class.java))
        }
        binding.rowRecording.setOnClickListener {
            startActivity(Intent(this, RecordingSettingsActivity::class.java))
        }

        binding.switchDialpadTone.isChecked = Prefs.dialpadTone(this)
        binding.switchDialpadTone.setOnCheckedChangeListener { _, checked ->
            Prefs.setDialpadTone(this, checked)
        }
        binding.rowDialpadTone.setOnClickListener {
            binding.switchDialpadTone.isChecked = !binding.switchDialpadTone.isChecked
        }
    }


    private fun updateFloatingSummary() {
        binding.floatingValue.text = getString(
            if (Prefs.floatingDialButton(this)) R.string.setting_on
            else R.string.setting_off
        )
    }

    override fun onResume() {
        super.onResume()
        binding.themeValue.text = getString(
            when (Prefs.themeMode(this)) {
                Prefs.THEME_LIGHT -> R.string.setting_theme_light
                Prefs.THEME_DARK -> R.string.setting_theme_dark
                else -> R.string.setting_theme_system
            }
        )
        binding.accountsValue.text =
            if (CallingAccounts.isMultiSim(this)) getString(R.string.setting_default_sim)
            else getString(R.string.setting_single_sim)
        updateFloatingSummary()
        binding.redirectValue.text = getString(
            if (DialerRedirectService.isEnabled(this)) R.string.setting_on
            else R.string.setting_off
        )
        binding.keepAliveValue.text = getString(
            if (Prefs.keepAlive(this)) R.string.setting_on
            else R.string.setting_keepalive_summary
        )
    }

    private fun showThemeDialog() {
        val labels = arrayOf(
            getString(R.string.setting_theme_light),
            getString(R.string.setting_theme_dark),
            getString(R.string.setting_theme_system)
        )
        val modes = intArrayOf(Prefs.THEME_LIGHT, Prefs.THEME_DARK, Prefs.THEME_SYSTEM)
        val current = modes.indexOf(Prefs.themeMode(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Prefs.setThemeMode(this, modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
