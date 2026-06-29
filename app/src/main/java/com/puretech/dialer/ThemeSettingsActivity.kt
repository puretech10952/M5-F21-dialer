package com.puretech.dialer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityThemeSettingsBinding

/**
 * Theme page: light/dark/system appearance plus an in-app color picker that works
 * on every device, even those without Material You. See [DialerThemes] / [ThemeManager].
 */
class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeSettingsBinding

    private data class Swatch(val id: Int, val name: String, val color: Int?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }

        // Appearance (light / dark / system).
        when (Prefs.themeMode(this)) {
            Prefs.THEME_LIGHT -> binding.modeLight.isChecked = true
            Prefs.THEME_DARK -> binding.modeDark.isChecked = true
            else -> binding.modeSystem.isChecked = true
        }
        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.modeLight -> Prefs.THEME_LIGHT
                R.id.modeDark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            if (mode != Prefs.themeMode(this)) Prefs.setThemeMode(this, mode)
        }

        buildSwatches()
    }

    private fun buildSwatches() {
        val items = buildList {
            add(Swatch(DialerThemes.DEFAULT, getString(R.string.theme_default), null))
            DialerThemes.list.forEach { add(Swatch(it.id, it.name, it.swatch)) }
        }
        val selected = Prefs.colorTheme(this)
        val grid = binding.swatchGrid
        val cols = grid.columnCount

        items.forEachIndexed { i, item ->
            val cell = layoutInflater.inflate(R.layout.item_theme_swatch, grid, false)
            val swatch = cell.findViewById<View>(R.id.swatch)
            val check = cell.findViewById<ImageView>(R.id.check)
            cell.findViewById<TextView>(R.id.name).text = item.name

            if (item.color == null) {
                swatch.setBackgroundResource(R.drawable.bg_swatch_dynamic)
                swatch.backgroundTintList = null
            } else {
                swatch.setBackgroundResource(R.drawable.bg_swatch)
                swatch.backgroundTintList = ColorStateList.valueOf(item.color)
            }
            check.visibility = if (item.id == selected) View.VISIBLE else View.GONE

            cell.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % cols, 1f)
                rowSpec = GridLayout.spec(i / cols)
            }
            cell.setOnClickListener {
                if (item.id != selected) {
                    Prefs.setColorTheme(this, item.id)
                    recreate()   // re-apply the overlay and refresh the checkmarks
                }
            }
            grid.addView(cell)
        }
    }
}
