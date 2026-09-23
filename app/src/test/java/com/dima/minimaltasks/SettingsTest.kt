package com.dima.minimaltasks

import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun unknownThemeModeFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
    }

    @Test
    fun themeModeRoundTripsStoredName() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStored(mode.name))
        }
    }

    @Test
    fun dayBeforeDigestDefaultsToNoonAndEnabled() {
        val state = SettingsState()
        assertTrue(state.dayBeforeEnabled)
        assertEquals(12 * 60, state.dayBeforeMinuteOfDay)
        assertEquals(12 * 60, state.dayBeforeMinuteOrNull)
        assertNull(SettingsState(dayBeforeEnabled = false).dayBeforeMinuteOrNull)
    }

    @Test
    fun minuteOfDayIsSanitizedIntoTheDay() {
        assertEquals(0, SettingsState.sanitizeMinuteOfDay(-5))
        assertEquals(1439, SettingsState.sanitizeMinuteOfDay(24 * 60))
        assertEquals(600, SettingsState.sanitizeMinuteOfDay(600))
    }
}
