package com.dima.minimaltasks.data

import androidx.room.withTransaction
import com.dima.minimaltasks.data.local.AppDatabase
import com.dima.minimaltasks.data.local.AttachmentDao
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.data.local.TaskDao
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.local.TaskWithAttachments
import com.dima.minimaltasks.domain.RecurrenceCalculator
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class CompletionResult(
    val completedTask: TaskEntity,
    val generatedTask: TaskEntity?,
)

data class UndoCompletionResult(
    val restoredTask: TaskEntity,
    val removedChildIds: List<String>,
)

data class TaskSaveRequest(
    val task: TaskEntity,
    val isNew: Boolean,
    val removedAttachments: List<AttachmentEntity> = emptyList(),
    val newAttachments: List<AttachmentEntity> = emptyList(),
)

class TaskRepository(
    private val database: AppDatabase,
    private val taskDao: TaskDao = database.taskDao(),
    private val attachmentDao: AttachmentDao = database.attachmentDao(),
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeDateRange(startInclusive: Long, endExclusive: Long): Flow<List<TaskEntity>> =
        taskDao.observeDateRange(startInclusive, endExclusive)

    fun observeTask(taskId: String): Flow<TaskWithAttachments?> = taskDao.observeWithAttachments(taskId)

    fun observeAttachments(taskId: String): Flow<List<AttachmentEntity>> = attachmentDao.observeForTask(taskId)

    suspend fun findTask(taskId: String): TaskEntity? = taskDao.findById(taskId)

    suspend fun snapshot(): List<TaskEntity> = taskDao.snapshot()

    suspend fun snapshotWithAttachments(): List<TaskWithAttachments> = taskDao.snapshotWithAttachments()

    suspend fun findAttachments(taskId: String): List<AttachmentEntity> = attachmentDao.findForTask(taskId)

    suspend fun insertTask(task: TaskEntity) = taskDao.insert(task)

    /** Active, timed tasks that are still ahead: the only ones whose alarms need re-arming. */
    suspend fun findSchedulable(nowMillis: Long): List<TaskEntity> = taskDao.findSchedulable(nowMillis)

    /**
     * Applies [transform] to the current row, not to a UI snapshot, so a concurrent change (e.g.
     * completion from a notification) survives. A null transform result means "nothing to write".
     * Returns the written row, or null when the task is gone or unchanged.
     */
    suspend fun updateTask(taskId: String, transform: (TaskEntity) -> TaskEntity?): TaskEntity? =
        database.withTransaction {
            val current = taskDao.findById(taskId) ?: return@withTransaction null
            val updated = transform(current) ?: return@withTransaction null
            check(taskDao.update(updated) == 1) { "Task not found: $taskId" }
            updated
        }

    /** Returns the row as written. For an existing task only the editor-owned fields are taken from [TaskSaveRequest.task]. */
    suspend fun saveTaskWithAttachments(request: TaskSaveRequest): TaskEntity = database.withTransaction {
        val task = if (request.isNew) {
            request.task.also { taskDao.insert(it) }
        } else {
            // The editor may have been open while the task was completed from a notification
            // or detached from a deleted parent: keep that state from the current row.
            val current = checkNotNull(taskDao.findById(request.task.id)) { "Task not found: ${request.task.id}" }
            request.task.copy(
                completed = current.completed,
                completedAt = current.completedAt,
                createdAt = current.createdAt,
                recurrenceParentTaskId = current.recurrenceParentTaskId,
            ).also { check(taskDao.update(it) == 1) { "Task not found: ${it.id}" } }
        }
        request.removedAttachments.forEach { attachment ->
            check(attachmentDao.deleteById(attachment.id) == 1) { "Attachment not found: ${attachment.id}" }
        }
        if (request.newAttachments.isNotEmpty()) attachmentDao.insertAll(request.newAttachments)
        task
    }

    suspend fun deleteTask(taskId: String): Boolean = database.withTransaction {
        // The generated continuation outlives the occurrence it came from, so it must not
        // keep pointing at a deleted row (backup validation rejects dangling parents).
        taskDao.detachChild(taskId)
        taskDao.deleteById(taskId) == 1
    }

    suspend fun addAttachment(attachment: AttachmentEntity) = attachmentDao.insert(attachment)

    suspend fun deleteAttachment(attachmentId: String): Boolean =
        database.withTransaction { attachmentDao.deleteById(attachmentId) == 1 }

    suspend fun completeTask(taskId: String, nowMillis: Long = System.currentTimeMillis()): CompletionResult? =
        database.withTransaction {
            val original = taskDao.findById(taskId) ?: return@withTransaction null
            if (original.completed) return@withTransaction null

            if (taskDao.markCompleted(taskId, nowMillis, nowMillis) != 1) return@withTransaction null

            val nextDueAt = recurrenceDueAt(original, nowMillis)
            val generated = if (nextDueAt == null) {
                null
            } else {
                val child = original.copy(
                    id = UUID.randomUUID().toString(),
                    dueAt = nextDueAt,
                    completed = false,
                    completedAt = null,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                    recurrenceParentTaskId = original.id,
                )
                taskDao.insertIfAbsent(child)
                taskDao.findChildFor(original.id)
            }
            CompletionResult(original.copy(completed = true, completedAt = nowMillis, updatedAt = nowMillis), generated)
        }

    suspend fun undoCompletion(taskId: String, nowMillis: Long = System.currentTimeMillis()): UndoCompletionResult? =
        database.withTransaction {
            val task = taskDao.findById(taskId) ?: return@withTransaction null
            if (!task.completed) return@withTransaction null
            if (taskDao.markIncomplete(taskId, nowMillis) != 1) return@withTransaction null
            val removedChildIds = deleteUntouchedChild(taskId)
            UndoCompletionResult(
                restoredTask = task.copy(completed = false, completedAt = null, updatedAt = nowMillis),
                removedChildIds = removedChildIds,
            )
        }

    suspend fun insertTasks(tasks: List<TaskEntity>) = taskDao.insertAll(tasks)

    suspend fun insertAttachments(attachments: List<AttachmentEntity>) = attachmentDao.insertAll(attachments)

    suspend fun replaceAll(tasks: List<TaskEntity>, attachments: List<AttachmentEntity>) {
        database.withTransaction {
            attachmentDao.deleteAll()
            taskDao.deleteAll()
            if (tasks.isNotEmpty()) taskDao.insertAll(tasks)
            if (attachments.isNotEmpty()) attachmentDao.insertAll(attachments)
        }
    }

    /**
     * Removes the occurrence generated by completing [taskId], but only while it is still the
     * untouched copy: once completed or edited (any change bumps `updatedAt`) it is the user's
     * own data and stays, together with whatever followed it. An untouched child cannot have
     * children of its own — generating one requires completing it.
     */
    private suspend fun deleteUntouchedChild(taskId: String): List<String> {
        val child = taskDao.findChildFor(taskId) ?: return emptyList()
        if (child.completed || child.updatedAt != child.createdAt) return emptyList()
        taskDao.deleteById(child.id)
        return listOf(child.id)
    }

    private fun recurrenceDueAt(task: TaskEntity, nowMillis: Long): Long? {
        val unit = task.recurrenceUnit ?: return null
        val dueAt = task.dueAt ?: return null
        return RecurrenceCalculator.nextOccurrence(
            dueAtMillis = dueAt,
            unit = unit,
            interval = task.recurrenceInterval,
            weekdayMask = task.recurrenceWeekdayMask,
            zoneId = task.recurrenceZoneId,
            nowMillis = nowMillis,
        )
    }
}
