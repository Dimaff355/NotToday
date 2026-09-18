package com.dima.minimaltasks.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        ORDER BY CASE WHEN completed = 0 THEN 0 ELSE 1 END,
                 CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
                 dueAt ASC,
                 createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE dueAt >= :startInclusive AND dueAt < :endExclusive
        ORDER BY dueAt ASC, createdAt DESC
        """,
    )
    fun observeDateRange(startInclusive: Long, endExclusive: Long): Flow<List<TaskEntity>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun observeWithAttachments(taskId: String): Flow<TaskWithAttachments?>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun findById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC")
    suspend fun snapshot(): List<TaskEntity>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY createdAt ASC")
    suspend fun snapshotWithAttachments(): List<TaskWithAttachments>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks WHERE recurrenceParentTaskId = :parentId LIMIT 1")
    suspend fun findChildFor(parentId: String): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity): Int

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String): Int

    @Query("DELETE FROM tasks WHERE recurrenceParentTaskId = :parentId")
    suspend fun deleteChildFor(parentId: String): Int

    @Query("UPDATE tasks SET completed = 1, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :taskId AND completed = 0")
    suspend fun markCompleted(taskId: String, completedAt: Long, updatedAt: Long): Int

    @Query("UPDATE tasks SET completed = 0, completedAt = NULL, updatedAt = :updatedAt WHERE id = :taskId AND completed = 1")
    suspend fun markIncomplete(taskId: String, updatedAt: Long): Int
}
