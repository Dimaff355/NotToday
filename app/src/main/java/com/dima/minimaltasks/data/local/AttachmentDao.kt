package com.dima.minimaltasks.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun observeForTask(taskId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY createdAt ASC")
    suspend fun findForTask(taskId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun findById(attachmentId: String): AttachmentEntity?

    @Insert
    suspend fun insert(attachment: AttachmentEntity)

    @Insert
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id = :attachmentId")
    suspend fun deleteById(attachmentId: String): Int

    @Query("DELETE FROM attachments WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String): Int

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
