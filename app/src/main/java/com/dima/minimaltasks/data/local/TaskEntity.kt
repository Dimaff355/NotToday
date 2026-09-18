package com.dima.minimaltasks.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZoneId
import java.util.UUID

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["dueAt"]),
        Index(value = ["recurrenceParentTaskId"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val isPriority: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val recurrenceUnit: RecurrenceUnit? = null,
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdayMask: Int = 0,
    val recurrenceZoneId: String = ZoneId.systemDefault().id,
    val recurrenceParentTaskId: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Task title must not be blank" }
        require(recurrenceInterval in 1..999) { "Recurrence interval must be between 1 and 999" }
        require(recurrenceWeekdayMask in 0..0b1111111) { "recurrenceWeekdayMask must contain only seven weekday bits" }
        ZoneId.of(recurrenceZoneId)
    }
}
