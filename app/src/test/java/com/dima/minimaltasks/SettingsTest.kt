package com.dima.minimaltasks

import com.dima.minimaltasks.data.settings.ThemeMode
import org.junit.Assert.assertEquals
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
}
