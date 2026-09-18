package com.dima.minimaltasks.ui

/** Permissions the first-run screen offers to grant up front. */
enum class OnboardingPermission { NOTIFICATIONS, EXACT_ALARMS }

/**
 * Which of them the platform can actually ask for: notifications became a runtime
 * permission in API 33, exact alarms an app-op in API 31. Older devices get neither row.
 */
object OnboardingModel {
    const val NOTIFICATION_PERMISSION_API = 33
    const val EXACT_ALARM_PERMISSION_API = 31

    fun permissions(apiLevel: Int): List<OnboardingPermission> = buildList {
        if (apiLevel >= NOTIFICATION_PERMISSION_API) add(OnboardingPermission.NOTIFICATIONS)
        if (apiLevel >= EXACT_ALARM_PERMISSION_API) add(OnboardingPermission.EXACT_ALARMS)
    }
}
