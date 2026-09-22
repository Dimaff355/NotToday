package com.dima.minimaltasks

import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.ui.TaskListSections
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskListSectionsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 9, 22)

    private fun dateAt(date: LocalDate, hour: Int = 0): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: String,
        due: Long? = null,
        completed: Boolean = false,
        completedAt: Long? = null,
    ) = TaskEntity(
        id = id,
        title = "task $id",
        dueAt = due,
        completed = completed,
        completedAt = completedAt,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `today keeps current and overdue active tasks, sends future and undated away`() {
        val tasks = listOf(
            task("future", dateAt(today.plusDays(3))),
            task("undated"),
            task("today", dateAt(today)),
            task("yesterday", dateAt(today.minusDays(1))),
            task("tomorrow", dateAt(today.plusDays(1), hour = 9)),
        )

        val sections = TaskListSections.today(tasks, today, zone)

        assertEquals(listOf("yesterday"), sections.overdue.map(TaskEntity::id))
        assertEquals(listOf("today"), sections.today.map(TaskEntity::id))
        assertEquals(emptyList<String>(), sections.completed.map(TaskEntity::id))
    }

    @Test
    fun `later keeps future and undated active tasks, sends today and overdue away`() {
        val tasks = listOf(
            task("future1", dateAt(today.plusDays(2))),
            task("future2", dateAt(today.plusDays(1))),
            task("undated"),
            task("today", dateAt(today)),
            task("overdue", dateAt(today.minusDays(2))),
        )

        val sections = TaskListSections.later(tasks, today, zone)

        assertEquals(listOf("future2", "future1"), sections.scheduled.map(TaskEntity::id))
        assertEquals(listOf("undated"), sections.someday.map(TaskEntity::id))
    }

    @Test
    fun `completed tasks stay in the tab their due date belongs to`() {
        val tasks = listOf(
            task("done-today", dateAt(today), completed = true, completedAt = dateAt(today, hour = 12)),
            task("done-overdue", dateAt(today.minusDays(1)), completed = true, completedAt = dateAt(today, hour = 12)),
            task("done-future", dateAt(today.plusDays(1)), completed = true, completedAt = dateAt(today, hour = 12)),
            task("done-undated", completed = true, completedAt = dateAt(today, hour = 12)),
        )

        val todaySections = TaskListSections.today(tasks, today, zone)
        val laterSections = TaskListSections.later(tasks, today, zone)

        assertEquals(setOf("done-today", "done-overdue"), todaySections.completed.map(TaskEntity::id).toSet())
        assertEquals(setOf("done-future", "done-undated"), laterSections.completed.map(TaskEntity::id).toSet())
    }

    @Test
    fun `overdue and scheduled sections are sorted by due date regardless of input order`() {
        val tasks = listOf(
            task("far", dateAt(today.minusDays(5))),
            task("near", dateAt(today.minusDays(1))),
            task("future-far", dateAt(today.plusDays(9))),
            task("future-near", dateAt(today.plusDays(1))),
        )

        val todaySections = TaskListSections.today(tasks, today, zone)
        val laterSections = TaskListSections.later(tasks, today, zone)

        assertEquals(listOf("far", "near"), todaySections.overdue.map(TaskEntity::id))
        assertEquals(listOf("future-near", "future-far"), laterSections.scheduled.map(TaskEntity::id))
    }

    @Test
    fun `completed section lists most recent first`() {
        val tasks = listOf(
            task("older", dateAt(today), completed = true, completedAt = dateAt(today, hour = 10)),
            task("newer", dateAt(today), completed = true, completedAt = dateAt(today, hour = 15)),
        )

        val sections = TaskListSections.today(tasks, today, zone)

        assertEquals(listOf("newer", "older"), sections.completed.map(TaskEntity::id))
    }

    @Test
    fun `tasks with a later date but earlier instant stay future across midnight zones`() {
        // 23:00 Moscow on the 23rd is still "after today" in Moscow even though it is
        // the 22nd in UTC — grouping must use the zone it is given.
        val due = LocalDate.of(2026, 9, 23).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val sections = TaskListSections.today(listOf(task("late", due)), today, zone)

        assertEquals(emptyList<String>(), sections.today.map(TaskEntity::id))
    }
}
