package com.dima.minimaltasks.data.settings

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val completionSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
)
