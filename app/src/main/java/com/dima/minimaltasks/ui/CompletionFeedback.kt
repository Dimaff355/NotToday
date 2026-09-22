package com.dima.minimaltasks.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.dima.minimaltasks.data.settings.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CompletionFeedbackDecision(
    val playSound: Boolean,
    val vibrate: Boolean,
)

object CompletionFeedbackPolicy {
    fun from(settings: SettingsState): CompletionFeedbackDecision = CompletionFeedbackDecision(
        playSound = settings.completionSoundEnabled,
        vibrate = settings.vibrationEnabled,
    )
}

object CompletionFeedback {
    private const val TONE_VOLUME = 35
    private const val TONE_DURATION_MILLIS = 200L

    /**
     * Distinct confirmation fired at the tap itself, in sync with the checkbox starting to fill.
     * `performHapticFeedback` already respects the system-wide haptics setting on top of ours.
     */
    fun completionHaptic(view: View, decision: CompletionFeedbackDecision) {
        if (!decision.vibrate) return
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    /** One soft tick when the swipe-to-tomorrow gesture commits. */
    fun swipeHaptic(view: View, decision: CompletionFeedbackDecision) {
        if (!decision.vibrate) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Completion tone; the haptic lives in [completionHaptic] so the two never double up. */
    suspend fun play(decision: CompletionFeedbackDecision) {
        if (!decision.playSound) return

        withContext(Dispatchers.Main.immediate) {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }.getOrNull()
                ?: return@withContext
            try {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION_MILLIS.toInt())
                delay(TONE_DURATION_MILLIS)
            } finally {
                tone.release()
            }
        }
    }
}
