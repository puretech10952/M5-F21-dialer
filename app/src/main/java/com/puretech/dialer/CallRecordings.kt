package com.puretech.dialer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.io.File

/**
 * Organizes call recordings into a sub-folder of Music.
 *
 * The system recorder (`com.mediatek.callrecorder`) always writes to
 * `/storage/emulated/0/Music`, and its path isn't a setting we can change. So
 * after a call that was recorded ends, we move the freshly written audio file(s)
 * into `Music/Call recordings/`. The recorder owns those files, so the move
 * needs "All files access" (MANAGE_EXTERNAL_STORAGE) — without it recording
 * still works, the files just stay loose in Music.
 */
object CallRecordings {

    /** Sub-folder under Music where recordings are collected. */
    const val SUBFOLDER = "Call recordings"

    private val musicDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

    private val audioExts = setOf("aac", "amr", "mp3", "m4a", "wav", "3gp", "awb", "ogg")

    private val handler = Handler(Looper.getMainLooper())

    /** Earliest moment in the current call at which recording was started. */
    private var startedAt = 0L

    /** Note that recording began (keeps the earliest start within a call). */
    fun markStarted() {
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
    }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /**
     * After the call ends, move any audio the recorder wrote during it into the
     * sub-folder. Delayed a little so the recorder has finished flushing.
     */
    fun scheduleOrganize(context: Context) {
        if (startedAt == 0L) return
        val since = startedAt - 5_000
        startedAt = 0L
        if (!hasAllFilesAccess()) return
        val appCtx = context.applicationContext
        handler.postDelayed({ organize(appCtx, since) }, 2_500)
    }

    private fun organize(context: Context, since: Long) {
        try {
            val music = musicDir
            val dest = File(music, SUBFOLDER)
            if (!dest.exists() && !dest.mkdirs()) return
            val files = music.listFiles { f ->
                f.isFile && f.lastModified() >= since &&
                    f.extension.lowercase() in audioExts
            } ?: return
            for (f in files) {
                val target = File(dest, f.name)
                if (f.renameTo(target)) {
                    // Refresh MediaStore for both the vanished and new locations.
                    MediaScannerConnection.scanFile(
                        context, arrayOf(f.absolutePath, target.absolutePath), null, null
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Open the system "All files access" screen for this app. */
    fun requestAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }
}
