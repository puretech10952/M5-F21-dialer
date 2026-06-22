package com.puretech.dialer

import android.content.Context
import android.util.TypedValue

/** Resolve a theme color attribute (e.g. colorOnSurface) to a color int. */
fun Context.themeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

/** Compact talk-time from a duration in seconds: "2h 13m", "5m 3s", "45s". */
fun Long.asTalkTime(): String {
    val total = if (this < 0) 0 else this
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
