package com.puretech.dialer

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays the real DTMF dual-tones you hear on a normal phone keypad — one
 * distinct two-frequency tone per key — rather than a generic UI click.
 *
 * Used by the dial screen (gated by [Prefs.dialpadTone]). The in-call keypad
 * sends DTMF through the live call instead (see [CallManager.playDtmf]).
 */
object DialpadTones {

    private const val VOLUME = 80          // ToneGenerator scale is 0..100
    private const val DURATION_MS = 150    // long enough to sound like a real tone

    private var generator: ToneGenerator? = null

    private fun gen(): ToneGenerator? {
        if (generator == null) {
            generator = try {
                ToneGenerator(AudioManager.STREAM_DTMF, VOLUME)
            } catch (e: Throwable) {
                null
            }
        }
        return generator
    }

    /** Play the DTMF tone for [c]. Non-dial characters (+ , ;) are ignored. */
    fun play(c: Char) {
        val tone = toneFor(c) ?: return
        try {
            gen()?.startTone(tone, DURATION_MS)
        } catch (e: Throwable) {
            // A long-lived ToneGenerator can occasionally die; rebuild next time.
            release()
        }
    }

    fun release() {
        try { generator?.release() } catch (_: Throwable) {}
        generator = null
    }

    private fun toneFor(c: Char): Int? = when (c) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }
}
