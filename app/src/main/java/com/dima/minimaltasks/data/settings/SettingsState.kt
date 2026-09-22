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
)
