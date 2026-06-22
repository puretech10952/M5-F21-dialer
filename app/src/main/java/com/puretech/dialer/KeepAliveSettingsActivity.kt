package com.puretech.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityKeepaliveSettingsBinding

/**
 * Explains and toggles the keep-alive service, and offers a one-tap
 * battery-optimization exemption. Both together are what keep incoming calls
 * surfacing on ROMs that freeze background apps (e.g. DuraSpeed on the F21).
 */
class KeepAliveSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeepaliveSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeepaliveSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }

        binding.switchKeepAlive.isChecked = Prefs.keepAlive(this)
        binding.switchKeepAlive.setOnCheckedChangeListener { _, checked ->
            Prefs.setKeepAlive(this, checked)
            KeepAliveService.apply(this)
        }
        binding.rowToggle.setOnClickListener {
            binding.switchKeepAlive.isChecked = !binding.switchKeepAlive.isChecked
        }

        binding.battery.setOnClickListener { requestIgnoreBatteryOptimizations() }
    }

    override fun onResume() {
        super.onResume()
        binding.batteryStatus.text = getString(
            if (isIgnoringBattery()) R.string.keepalive_battery_on
            else R.string.keepalive_battery_off
        )
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }
}
