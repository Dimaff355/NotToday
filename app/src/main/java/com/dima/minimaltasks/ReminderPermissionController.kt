package com.dima.minimaltasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dima.minimaltasks.notifications.AlarmAccuracy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Activity-owned bridge for runtime notification and exact-alarm permissions. */
class ReminderPermissionController(
    private val activity: ComponentActivity,
    private val app: MinimalTasksApplication,
) {
    private enum class PromptKind { EXPLICIT, AUTOMATIC }

    private var pendingPrompt: PromptKind? = null
    private var automaticPromptInFlight = false
    private var exactSettingsLaunched = false
    private val _alarmAccuracy = MutableStateFlow(app.reminderScheduler.alarmAccuracy())
    val alarmAccuracy: StateFlow<AlarmAccuracy> = _alarmAccuracy.asStateFlow()
    private val _notificationsGranted = MutableStateFlow(hasNotificationPermission())
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val prompt = pendingPrompt
        pendingPrompt = null
        automaticPromptInFlight = false
        _notificationsGranted.value = granted
        activity.lifecycleScope.launch {
            if (granted) {
                app.reminderCoordinator.setNotificationsEnabled(true)
                maybeLaunchExactAlarmSettings()
            } else {
                app.reminderCoordinator.setNotificationsEnabled(false)
                if (prompt == PromptKind.EXPLICIT) {
                    Toast.makeText(activity, activity.getString(R.string.notifications_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun requestExplicit(enabled: Boolean) {
        if (!enabled) {
            activity.lifecycleScope.launch { app.reminderCoordinator.setNotificationsEnabled(false) }
            return
        }
        enableNotifications(PromptKind.EXPLICIT)
    }

    /** Opens the exact-alarm screen on demand; the one-shot guard only suppresses automatic launches. */
    fun requestExactAlarmSettings() {
        exactSettingsLaunched = false
        maybeLaunchExactAlarmSettings()
    }

    fun requestAutomaticIfNeeded(hasEligibleTimedTask: Boolean) {
        if (!hasEligibleTimedTask || Build.VERSION.SDK_INT < 33 || hasNotificationPermission() || automaticPromptInFlight) return
        activity.lifecycleScope.launch {
            val state = app.settingsRepository.state.first()
            if (!state.notificationsEnabled || state.notificationPermissionAsked || automaticPromptInFlight) return@launch
            automaticPromptInFlight = true
            app.settingsRepository.markNotificationPermissionAsked()
            pendingPrompt = PromptKind.AUTOMATIC
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun onResume() {
        _alarmAccuracy.value = app.reminderScheduler.alarmAccuracy()
        _notificationsGranted.value = hasNotificationPermission()
        activity.lifecycleScope.launch {
            app.reminderCoordinator.reconcile()
            _alarmAccuracy.value = app.reminderScheduler.alarmAccuracy()
        }
    }

    private fun enableNotifications(promptKind: PromptKind) {
        if (Build.VERSION.SDK_INT < 33 || hasNotificationPermission()) {
            activity.lifecycleScope.launch {
                app.reminderCoordinator.setNotificationsEnabled(true)
                maybeLaunchExactAlarmSettings()
            }
            return
        }
        pendingPrompt = promptKind
        if (promptKind == PromptKind.EXPLICIT) {
            // The explicit request is the one prompt the user gets: don't ask again automatically.
            activity.lifecycleScope.launch { app.settingsRepository.markNotificationPermissionAsked() }
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun maybeLaunchExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31 || app.reminderScheduler.canScheduleExactAlarms() || exactSettingsLaunched) return
        exactSettingsLaunched = true
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        }.onFailure {
            exactSettingsLaunched = false
        }
    }
}
