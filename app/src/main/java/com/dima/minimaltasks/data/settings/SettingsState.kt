package com.dima.minimaltasks.data.settings

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val completionSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
    val welcomeCompleted: Boolean = false,
    /** One-time «swipe a task left to move it to tomorrow» hint has been seen. */
    val swipeHintDismissed: Boolean = false,
    /** Day-before digest: one «due tomorrow» notification, local minute of day (0..1439). */
    val dayBeforeEnabled: Boolean = true,
    val dayBeforeMinuteOfDay: Int = DEFAULT_DAY_BEFORE_MINUTE_OF_DAY,
) {
    /** Digest alarm minute for the scheduler, or null when the digest is off. */
    val dayBeforeMinuteOrNull: Int?
        get() = if (dayBeforeEnabled) dayBeforeMinuteOfDay else null

    companion object {
        const val DEFAULT_DAY_BEFORE_MINUTE_OF_DAY = 12 * 60
        const val MAX_MINUTE_OF_DAY = 24 * 60 - 1

        /** Stored values may predate validation; keep the minute inside the day. */
        fun sanitizeMinuteOfDay(value: Int): Int = value.coerceIn(0, MAX_MINUTE_OF_DAY)
    }
}
