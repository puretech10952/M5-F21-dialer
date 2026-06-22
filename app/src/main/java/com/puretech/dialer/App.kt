package com.puretech.dialer

import android.app.Application
import com.google.android.material.color.DynamicColors

/** Applies the saved theme + device dynamic color before any activity is created. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Unlock vendor hidden APIs (MTK InCallService.doMtkAction) for call recording.
        HiddenApi.unseal()
        Prefs.applyTheme(Prefs.themeMode(this))
        // Material You: derive the app's colors from the device's theme/wallpaper
        // palette (Android 12+). Updates automatically when the device theme changes.
        // No-op on older devices, which keep the built-in M3 palette.
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Best-effort voicemail (message-waiting) watcher; no-op without permission.
        VoicemailMonitor.start(this)
        // Keep-alive foreground service (opt-in) so incoming calls still surface
        // on ROMs that freeze background apps. No-op unless the user enabled it.
        KeepAliveService.start(this)
    }
}
