package com.puretech.dialer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.WeakHashMap

/**
 * Applies the user's chosen in-app color theme ([DialerThemes]) to every activity,
 * so it works even on devices without Material You. The overlay is merged onto the
 * activity theme in [onActivityPreCreated] (before any view inflates), and runs
 * AFTER DynamicColors is installed so a preset overrides the device dynamic color.
 * When the user changes the theme, activities that were created with a different id
 * recreate themselves on resume.
 */
object ThemeManager {

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            private val createdWith = WeakHashMap<Activity, Int>()

            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                val id = Prefs.colorTheme(activity)
                DialerThemes.styleFor(id)?.let { activity.theme.applyStyle(it, true) }
                createdWith[activity] = id
            }

            override fun onActivityResumed(activity: Activity) {
                val createdId = createdWith[activity] ?: return
                if (createdId != Prefs.colorTheme(activity)) activity.recreate()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
