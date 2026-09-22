package com.dima.minimaltasks.ui

import com.dima.minimaltasks.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sections of the «Сегодня» tab: what needs attention now, and what is already done. */
data class TodaySections(
    val overdue: List<TaskEntity>,
    val today: List<TaskEntity>,
    val completed: List<TaskEntity>,
) {
    val activeCount: Int get() = overdue.size + today.size
}

/** Sections of the «Не сегодня» tab: future plans and dateless tasks. */
data class LaterSections(
    val scheduled: List<TaskEntity>,
    val someday: List<TaskEntity>,
    val completed: List<TaskEntity>,
)

/**
 * Splits every task between the two list tabs by the local due date. A task stays in the tab its
 * date belongs to even after completion, so finishing something never makes it jump between tabs;
 * only the overdue section is active-only. Sections are sorted independently of input order:
 * by due date where there is one, most recently completed first otherwise.
 */
object TaskListSections {
    fun today(tasks: List<TaskEntity>, today: LocalDate, zoneId: ZoneId): TodaySections {
        val overdue = mutableListOf<TaskEntity>()
        val current = mutableListOf<TaskEntity>()
        val completed = mutableListOf<TaskEntity>()
        tasks.forEach { task ->
            val dueDate = task.dueAt?.localDate(zoneId)
            when {
                task.completed -> if (dueDate != null && !dueDate.isAfter(today)) completed += task
                dueDate == null || dueDate.isAfter(today) -> Unit // «Не сегодня» territory
                dueDate.isBefore(today) -> overdue += task
                else -> current += task
            }
        }
        return TodaySections(
            overdue = overdue.sortedBy(TaskEntity::dueAt),
            today = current.sortedBy(TaskEntity::dueAt),
            completed = completed.sortedByDescending(TaskEntity::completedAt),
        )
    }

    fun later(tasks: List<TaskEntity>, today: LocalDate, zoneId: ZoneId): LaterSections {
        val scheduled = mutableListOf<TaskEntity>()
        val someday = mutableListOf<TaskEntity>()
        val completed = mutableListOf<TaskEntity>()
        tasks.forEach { task ->
            val dueDate = task.dueAt?.localDate(zoneId)
            when {
                task.completed && (dueDate == null || dueDate.isAfter(today)) -> completed += task
                !task.completed && dueDate == null -> someday += task
                !task.completed && dueDate != null && dueDate.isAfter(today) -> scheduled += task
                else -> Unit // «Сегодня» territory
            }
        }
        return LaterSections(
            scheduled = scheduled.sortedBy(TaskEntity::dueAt),
            someday = someday,
            completed = completed.sortedByDescending(TaskEntity::completedAt),
        )
    }

    private fun Long.localDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}
