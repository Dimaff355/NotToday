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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
        assertEquals(ReminderIdentity.dayBeforeAlarmRequestCode(), ReminderIdentity.dayBeforeAlarmRequestCode())
        assertNotEquals(ReminderIdentity.dayBeforeAlarmRequestCode(), ReminderIdentity.dayBeforeNotificationId())
        assertTrue(ReminderIdentity.dayBeforeNotificationId() > 0)
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

    @Test
    fun day_before_fires_today_until_its_time_then_tomorrow() {
        val zone = ZoneId.of("Europe/Moscow") // fixed offset since 2014, no DST
        val noon = ZonedDateTime.of(2025, 4, 24, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val tomorrowNoon = ZonedDateTime.of(2025, 4, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(noon, ReminderScheduling.nextDayBeforeAt(720, noon - 60_000, zone))
        assertEquals(tomorrowNoon, ReminderScheduling.nextDayBeforeAt(720, noon, zone))
        assertEquals(tomorrowNoon, ReminderScheduling.nextDayBeforeAt(720, noon + 60_000, zone))
        // An out-of-range minute is clamped to the last minute of the day.
        val lastMinute = ZonedDateTime.of(2025, 4, 24, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(lastMinute, ReminderScheduling.nextDayBeforeAt(24 * 60, noon - 2 * 60_000, zone))
    }

    @Test
    fun day_before_skips_the_dst_gap_forward() {
        val zone = ZoneId.of("Europe/Berlin")
        // 2025-03-30 jumps 02:00→03:00; local 02:30 does not exist and lands on 03:30 CEST.
        val beforeGap = ZonedDateTime.of(2025, 3, 30, 1, 0, 0, 0, zone).toInstant().toEpochMilli()
        val shifted = ZonedDateTime.of(2025, 3, 30, 3, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(shifted, ReminderScheduling.nextDayBeforeAt(150, beforeGap, zone))
    }

    @Test
    fun tasks_due_on_pick_active_local_date_tasks_sorted_by_due_time() {
        val zone = ZoneId.of("Europe/Moscow")
        val date = LocalDate.of(2025, 4, 25)
        val morning = ZonedDateTime.of(2025, 4, 25, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val evening = ZonedDateTime.of(2025, 4, 25, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextDay = ZonedDateTime.of(2025, 4, 26, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(id = "evening", dueAt = evening),
            task(id = "later-created", dueAt = morning, createdAt = 2),
            task(id = "earlier-created", dueAt = morning, createdAt = 1),
            task(id = "completed", dueAt = morning, completed = true),
            task(id = "next-day", dueAt = nextDay),
            task(id = "no-date", dueAt = null),
        )

        val due = ReminderScheduling.tasksDueOn(tasks, date, zone)

        assertEquals(listOf("earlier-created", "later-created", "evening"), due.map(TaskEntity::id))
    }

    private fun task(
        id: String = "test-task",
        dueAt: Long?,
        dueHasTime: Boolean = false,
        completed: Boolean = false,
        createdAt: Long = now,
    ) = TaskEntity(
        id = id,
        title = "Test",
        dueAt = dueAt,
        dueHasTime = dueHasTime,
        completed = completed,
        createdAt = createdAt,
        updatedAt = now,
    )
}
