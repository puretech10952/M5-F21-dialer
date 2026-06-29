package com.puretech.dialer

/**
 * The fixed set of in-app color themes the user can pick regardless of whether
 * the device supports Material You. Each maps to a ThemeOverlay defined in
 * res/values{,-night}/color_themes.xml. Id 0 = Default (device dynamic color on
 * Android 12+, baseline palette otherwise).
 */
object DialerThemes {

    const val DEFAULT = 0

    data class Theme(val id: Int, val name: String, val styleRes: Int, val swatch: Int)

    val list = listOf(
        Theme(1, "Ocean Blue", R.style.ThemeOverlay_M5Dialer_Ocean, 0xFF04639A.toInt()),
        Theme(2, "Indigo", R.style.ThemeOverlay_M5Dialer_Indigo, 0xFF2D5CAE.toInt()),
        Theme(3, "Royal Purple", R.style.ThemeOverlay_M5Dialer_Purple, 0xFF754C9B.toInt()),
        Theme(4, "Magenta", R.style.ThemeOverlay_M5Dialer_Magenta, 0xFF9D3874.toInt()),
        Theme(5, "Rose", R.style.ThemeOverlay_M5Dialer_Rose, 0xFFA43658.toInt()),
        Theme(6, "Crimson", R.style.ThemeOverlay_M5Dialer_Crimson, 0xFFA53A3B.toInt()),
        Theme(7, "Sunset Orange", R.style.ThemeOverlay_M5Dialer_Sunset, 0xFF9C4415.toInt()),
        Theme(8, "Amber", R.style.ThemeOverlay_M5Dialer_Amber, 0xFF7A5900.toInt()),
        Theme(9, "Forest Green", R.style.ThemeOverlay_M5Dialer_Forest, 0xFF166D2E.toInt()),
        Theme(10, "Teal", R.style.ThemeOverlay_M5Dialer_Teal, 0xFF036A65.toInt()),
        Theme(11, "Sky Cyan", R.style.ThemeOverlay_M5Dialer_Sky, 0xFF036879.toInt()),
        Theme(12, "Slate", R.style.ThemeOverlay_M5Dialer_Slate, 0xFF4A6178.toInt()),
    )

    /** ThemeOverlay style for a preset id, or null for Default (id 0). */
    fun styleFor(id: Int): Int? = list.firstOrNull { it.id == id }?.styleRes
}
