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

    suspend fun updateTask(task: TaskEntity) = check(taskDao.update(task) == 1) { "Task not found: ${task.id}" }

    suspend fun saveTaskWithAttachments(request: TaskSaveRequest) {
        database.withTransaction {
            if (request.isNew) {
                taskDao.insert(request.task)
            } else {
                check(taskDao.update(request.task) == 1) { "Task not found: ${request.task.id}" }
            }
            request.removedAttachments.forEach { attachment ->
                check(attachmentDao.deleteById(attachment.id) == 1) { "Attachment not found: ${attachment.id}" }
            }
            if (request.newAttachments.isNotEmpty()) attachmentDao.insertAll(request.newAttachments)
        }
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
            val removedChildIds = deleteDescendants(taskId)
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

    /** Removes the occurrence chain generated by completing [taskId]; a task has at most one child. */
    private suspend fun deleteDescendants(taskId: String): List<String> {
        val removed = mutableListOf<String>()
        var currentId = taskDao.findChildFor(taskId)?.id
        while (currentId != null) {
            val nextId = taskDao.findChildFor(currentId)?.id
            taskDao.deleteById(currentId)
            removed += currentId
            currentId = nextId
        }
        return removed
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
