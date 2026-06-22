package com.puretech.dialer

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityAssistedDialingBinding
import java.util.Locale

/** Toggle assisted dialing and choose the home country used for country codes. */
class AssistedDialingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistedDialingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistedDialingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }

        binding.toggle.isChecked = Prefs.assistedDialing(this)
        binding.rowToggle.setOnClickListener {
            val on = !binding.toggle.isChecked
            binding.toggle.isChecked = on
            Prefs.setAssistedDialing(this, on)
        }
        binding.rowCountry.setOnClickListener { showCountryPicker() }
        updateCountryValue()
    }

    private fun updateCountryValue() {
        val override = Prefs.homeCountryOverride(this)
        binding.countryValue.text = if (override == null) {
            getString(R.string.country_auto, displayName(Prefs.detectedCountry(this)))
        } else {
            displayName(override)
        }
    }

    private fun displayName(iso: String): String =
        Locale("", iso).displayCountry.ifBlank { iso }

    private fun showCountryPicker() {
        // "Detect automatically" + every ISO country, sorted by display name.
        val isos = Locale.getISOCountries().sortedBy { displayName(it) }
        val labels = ArrayList<String>(isos.size + 1)
        labels.add(getString(R.string.country_auto, displayName(Prefs.detectedCountry(this))))
        isos.forEach { labels.add("${displayName(it)} ($it)") }

        AlertDialog.Builder(this)
            .setTitle(R.string.setting_home_country)
            .setItems(labels.toTypedArray()) { _, which ->
                Prefs.setHomeCountry(this, if (which == 0) null else isos[which - 1])
                updateCountryValue()
            }
            .show()
    }
}
