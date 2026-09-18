package com.dima.minimaltasks.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class AttachmentStore(filesDir: File) {
    private val root = File(filesDir, ROOT_DIR).canonicalFile
    private val stagingRoot = File(root, STAGING_DIR).canonicalFile

    init {
        root.mkdirs()
        stagingRoot.mkdirs()
        check(root.isDirectory && stagingRoot.isDirectory) { "Unable to create attachment storage" }
    }

    class StagedAttachment internal constructor(
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        internal val taskId: String,
        internal val stagedFile: File,
    )

    fun stage(
        taskId: String,
        source: InputStream,
        displayName: String,
        mimeType: String,
        existingCount: Int = 0,
    ): StagedAttachment {
        validateTaskId(taskId)
        require(existingCount in 0 until MAX_ATTACHMENTS_PER_TASK) {
            "A task can have at most $MAX_ATTACHMENTS_PER_TASK attachments"
        }
        val safeDisplayName = displayName.trim().take(MAX_DISPLAY_NAME_LENGTH)
        require(safeDisplayName.isNotEmpty()) { "Attachment name must not be blank" }
        val safeMimeType = mimeType.trim().ifEmpty { DEFAULT_MIME_TYPE }
        val taskStaging = File(stagingRoot, taskId).canonicalFile
        ensureChild(stagingRoot, taskStaging)
        taskStaging.mkdirs()
        val stagedFile = File(taskStaging, "${UUID.randomUUID()}$STAGING_SUFFIX").canonicalFile
        ensureChild(taskStaging, stagedFile)

        var copied = 0L
        try {
            FileOutputStream(stagedFile).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    copied += read
                    if (copied > MAX_FILE_BYTES) throw AttachmentTooLargeException(MAX_FILE_BYTES)
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } catch (error: Throwable) {
            stagedFile.delete()
            throw error
        }
        return StagedAttachment(safeDisplayName, safeMimeType, copied, taskId, stagedFile)
    }

    fun commit(staged: StagedAttachment): String {
        validateTaskId(staged.taskId)
        val stagingFile = staged.stagedFile.canonicalFile
        ensureChild(stagingRoot, stagingFile)
        require(stagingFile.isFile) { "Staged attachment is missing" }
        require(staged.sizeBytes <= MAX_FILE_BYTES) { "Attachment is too large" }

        val taskDirectory = File(root, staged.taskId).canonicalFile
        ensureChild(root, taskDirectory)
        taskDirectory.mkdirs()
        val target = File(taskDirectory, "${UUID.randomUUID()}$DATA_SUFFIX").canonicalFile
        ensureChild(taskDirectory, target)
        try {
            Files.move(stagingFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            Files.move(stagingFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return "${staged.taskId}/${target.name}"
    }

    /** Moves a committed file back to staging so a failed DB save can be retried. */
    fun restoreCommitted(
        relativePath: String,
        taskId: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
    ): StagedAttachment {
        validateTaskId(taskId)
        val source = resolve(relativePath)
        val taskStaging = File(stagingRoot, taskId).canonicalFile
        ensureChild(stagingRoot, taskStaging)
        taskStaging.mkdirs()
        val target = File(taskStaging, "${UUID.randomUUID()}$STAGING_SUFFIX").canonicalFile
        ensureChild(taskStaging, target)
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return StagedAttachment(displayName, mimeType, sizeBytes, taskId, target)
    }

    fun discard(staged: StagedAttachment) {
        val file = staged.stagedFile.canonicalFile
        ensureChild(stagingRoot, file)
        file.delete()
    }

    fun open(relativePath: String): InputStream = FileInputStream(resolve(relativePath))

    fun fileFor(relativePath: String): File = resolve(relativePath)

    fun delete(relativePath: String): Boolean {
        require(relativePath.isNotBlank()) { "Attachment path must not be blank" }
        val file = File(root, relativePath).canonicalFile
        ensureChild(root, file)
        require(file != root) { "Attachment path must point to a file" }
        return !file.exists() || file.delete()
    }

    fun cleanupTask(taskId: String): Boolean {
        validateTaskId(taskId)
        val directory = File(root, taskId).canonicalFile
        ensureChild(root, directory)
        return deleteRecursively(directory)
    }

    fun cleanupStaging(olderThanMillis: Long = DEFAULT_STAGING_AGE_MILLIS, nowMillis: Long = System.currentTimeMillis()): Int {
        require(olderThanMillis >= 0) { "olderThanMillis must not be negative" }
        if (!stagingRoot.isDirectory) return 0
        var removed = 0
        stagingRoot.listFiles()?.forEach { taskDirectory ->
            if (!taskDirectory.isDirectory) {
                if (taskDirectory.delete()) removed++
                return@forEach
            }
            taskDirectory.listFiles()?.forEach { file ->
                if (nowMillis - file.lastModified() >= olderThanMillis && deleteRecursively(file)) removed++
            }
            if (taskDirectory.listFiles().isNullOrEmpty()) taskDirectory.delete()
        }
        return removed
    }

    private fun resolve(relativePath: String): File {
        require(relativePath.isNotBlank()) { "Attachment path must not be blank" }
        val file = File(root, relativePath).canonicalFile
        ensureChild(root, file)
        require(file != root) { "Attachment path must point to a file" }
        require(file.isFile) { "Attachment file is missing" }
        return file
    }

    private fun validateTaskId(taskId: String) {
        require(TASK_ID_PATTERN.matches(taskId)) { "Unsafe task id" }
    }

    private fun ensureChild(parent: File, child: File) {
        val prefix = parent.path + File.separator
        require(child.path.startsWith(prefix)) { "Path escapes attachment storage" }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        return file.delete()
    }

    companion object {
        const val MAX_FILE_BYTES = 20L * 1024L * 1024L
        const val MAX_ATTACHMENTS_PER_TASK = 10
        const val ROOT_DIR = "attachments"
        const val STAGING_DIR = ".staging"
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private const val COPY_BUFFER_SIZE = 32 * 1024
        private const val MAX_DISPLAY_NAME_LENGTH = 255
        private const val STAGING_SUFFIX = ".part"
        private const val DATA_SUFFIX = ".bin"
        private const val DEFAULT_STAGING_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private val TASK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}

class AttachmentTooLargeException(maxBytes: Long) : IOException("Attachment exceeds $maxBytes bytes")
