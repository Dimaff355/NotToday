package com.dima.minimaltasks.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val completionSoundEnabled = booleanPreferencesKey("completion_sound_enabled")
        val vibrationEnabled = booleanPreferencesKey("vibration_enabled")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val notificationPermissionAsked = booleanPreferencesKey("notification_permission_asked")
        val welcomeCompleted = booleanPreferencesKey("welcome_completed")
    }

    val state: Flow<SettingsState> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            SettingsState(
                themeMode = ThemeMode.fromStored(preferences[Keys.themeMode]),
                completionSoundEnabled = preferences[Keys.completionSoundEnabled] ?: true,
                vibrationEnabled = preferences[Keys.vibrationEnabled] ?: true,
                notificationsEnabled = preferences[Keys.notificationsEnabled] ?: true,
                notificationPermissionAsked = preferences[Keys.notificationPermissionAsked] ?: false,
                welcomeCompleted = preferences[Keys.welcomeCompleted] ?: false,
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = context.settingsDataStore.edit {
        it[Keys.themeMode] = value.name
    }

    suspend fun setCompletionSoundEnabled(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.completionSoundEnabled] = value
    }

    suspend fun setVibrationEnabled(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.vibrationEnabled] = value
    }

    suspend fun setNotificationsEnabled(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.notificationsEnabled] = value
    }

    suspend fun markNotificationPermissionAsked() = context.settingsDataStore.edit {
        it[Keys.notificationPermissionAsked] = true
    }

    suspend fun markWelcomeCompleted() = context.settingsDataStore.edit {
        it[Keys.welcomeCompleted] = true
    }

    suspend fun replace(value: SettingsState) = context.settingsDataStore.edit {
        it[Keys.themeMode] = value.themeMode.name
        it[Keys.completionSoundEnabled] = value.completionSoundEnabled
        it[Keys.vibrationEnabled] = value.vibrationEnabled
        it[Keys.notificationsEnabled] = value.notificationsEnabled
        it[Keys.notificationPermissionAsked] = value.notificationPermissionAsked
        it[Keys.welcomeCompleted] = value.welcomeCompleted
    }
}
