package dev.icarus.app

import android.content.Context
import android.media.AudioManager

/** Best-effort call detection: telephony and voice-communication audio modes.
 * Third-party VoIP calls are not exposed to ordinary apps; audio mode is the
 * closest public signal and can miss a ringing call before audio starts. */
class CallMonitor(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun isActive(): Boolean = when (audio?.mode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_COMMUNICATION,
        -> true

        else -> false
    }
}
