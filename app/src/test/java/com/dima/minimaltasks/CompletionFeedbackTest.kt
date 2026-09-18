package com.dima.minimaltasks

import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.ui.CompletionFeedbackPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionFeedbackTest {
    @Test
    fun enabled_settings_enable_both_feedback_channels() {
        val decision = CompletionFeedbackPolicy.from(SettingsState())

        assertTrue(decision.playSound)
        assertTrue(decision.vibrate)
    }

    @Test
    fun settings_disable_each_channel_independently() {
        val soundOff = CompletionFeedbackPolicy.from(SettingsState(completionSoundEnabled = false))
        val vibrationOff = CompletionFeedbackPolicy.from(SettingsState(vibrationEnabled = false))

        assertFalse(soundOff.playSound)
        assertTrue(soundOff.vibrate)
        assertTrue(vibrationOff.playSound)
        assertFalse(vibrationOff.vibrate)
    }
}
