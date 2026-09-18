package com.dima.minimaltasks

import com.dima.minimaltasks.ui.OnboardingModel
import com.dima.minimaltasks.ui.OnboardingPermission
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingModelTest {
    @Test
    fun olderReleasesHaveNothingToAskFor() {
        assertEquals(emptyList<OnboardingPermission>(), OnboardingModel.permissions(26))
        assertEquals(emptyList<OnboardingPermission>(), OnboardingModel.permissions(30))
    }

    @Test
    fun exactAlarmsAppearFromApi31() {
        assertEquals(listOf(OnboardingPermission.EXACT_ALARMS), OnboardingModel.permissions(31))
        assertEquals(listOf(OnboardingPermission.EXACT_ALARMS), OnboardingModel.permissions(32))
    }

    @Test
    fun notificationsAppearFromApi33() {
        assertEquals(
            listOf(OnboardingPermission.NOTIFICATIONS, OnboardingPermission.EXACT_ALARMS),
            OnboardingModel.permissions(33),
        )
        assertEquals(
            listOf(OnboardingPermission.NOTIFICATIONS, OnboardingPermission.EXACT_ALARMS),
            OnboardingModel.permissions(37),
        )
    }
}
