package com.dima.minimaltasks

import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.notifications.AlarmScheduleMode
import com.dima.minimaltasks.notifications.ReminderIdentity
import com.dima.minimaltasks.notifications.ReminderScheduling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulingTest {
    private val now = 1_700_000_000_000L

    @Test
    fun only_active_timed_tasks_are_eligible() {
        assertFalse(ReminderScheduling.isEligible(task(dueAt = null, dueHasTime = false)))
        assertFalse(ReminderScheduling.isEligible(task(dueAt = now + 1_000, dueHasTime = false)))
        assertFalse(ReminderScheduling.isEligible(task(dueAt = now + 1_000, dueHasTime = true, completed = true)))
        assertTrue(ReminderScheduling.isEligible(task(dueAt = now + 1_000, dueHasTime = true)))
        assertFalse(ReminderScheduling.shouldSchedule(task(dueAt = now - 1, dueHasTime = true), now))
    }

    @Test
    fun deterministic_ids_are_stable_and_namespaced() {
        val id = "task-123"
        assertEquals(ReminderIdentity.alarmRequestCode(id), ReminderIdentity.alarmRequestCode(id))
        assertEquals(ReminderIdentity.notificationId(id), ReminderIdentity.notificationId(id))
        assertNotEquals(ReminderIdentity.alarmRequestCode(id), ReminderIdentity.alarmRequestCode("task-456"))
        assertNotEquals(ReminderIdentity.completeRequestCode(id), ReminderIdentity.snoozeRequestCode(id))
        assertTrue(ReminderIdentity.notificationId(id) > 0)
    }

    @Test
    fun exact_alarm_falls_back_without_permission() {
        assertEquals(AlarmScheduleMode.EXACT_ALLOW_IDLE, ReminderScheduling.scheduleMode(35, true))
        assertEquals(AlarmScheduleMode.ALLOW_IDLE, ReminderScheduling.scheduleMode(35, false))
        assertEquals(AlarmScheduleMode.ALLOW_IDLE, ReminderScheduling.scheduleMode(26, false))
        assertEquals(AlarmScheduleMode.INEXACT, ReminderScheduling.scheduleMode(22, false))
        assertEquals(com.dima.minimaltasks.notifications.AlarmAccuracy.EXACT, ReminderScheduling.alarmAccuracy(35, true))
        assertEquals(com.dima.minimaltasks.notifications.AlarmAccuracy.FALLBACK, ReminderScheduling.alarmAccuracy(35, false))
        assertEquals(com.dima.minimaltasks.notifications.AlarmAccuracy.STANDARD, ReminderScheduling.alarmAccuracy(30, false))
    }

    @Test
    fun snooze_is_ten_minutes_and_saturates() {
        assertEquals(now + ReminderScheduling.SNOOZE_MILLIS, ReminderScheduling.snoozeAt(now))
        assertEquals(Long.MAX_VALUE, ReminderScheduling.snoozeAt(Long.MAX_VALUE - 1))
    }

    private fun task(
        dueAt: Long?,
        dueHasTime: Boolean,
        completed: Boolean = false,
    ) = TaskEntity(
        id = "test-task",
        title = "Test",
        dueAt = dueAt,
        dueHasTime = dueHasTime,
        completed = completed,
        createdAt = now,
        updatedAt = now,
    )
}
