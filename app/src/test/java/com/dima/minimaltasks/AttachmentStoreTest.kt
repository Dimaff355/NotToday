package com.dima.minimaltasks

import com.dima.minimaltasks.data.AttachmentStore
import com.dima.minimaltasks.data.AttachmentTooLargeException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Arrays
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagedAttachmentCommitsToGeneratedPrivatePath() {
        val store = AttachmentStore(temporaryFolder.root)
        val staged = store.stage(
            taskId = "task_1",
            source = ByteArrayInputStream("hello".toByteArray()),
            displayName = "photo with spaces.jpg",
            mimeType = "image/jpeg",
        )

        val relativePath = store.commit(staged)
        assertTrue(relativePath.startsWith("task_1/"))
        assertTrue(relativePath.endsWith(".bin"))
        assertFalse(relativePath.contains("photo"))
        store.open(relativePath).use { input ->
            assertArrayEquals("hello".toByteArray(), input.readBytes())
        }
        assertTrue(store.delete(relativePath))
    }

    @Test(expected = AttachmentTooLargeException::class)
    fun stagingRejectsFilesOverTwentyMiB() {
        val store = AttachmentStore(temporaryFolder.root)
        store.stage(
            taskId = "task_1",
            source = RepeatingInputStream(AttachmentStore.MAX_FILE_BYTES + 1),
            displayName = "large.bin",
            mimeType = "application/octet-stream",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun stagingRejectsEleventhAttachment() {
        AttachmentStore(temporaryFolder.root).stage(
            taskId = "task_1",
            source = ByteArrayInputStream(ByteArray(0)),
            displayName = "file",
            mimeType = "text/plain",
            existingCount = AttachmentStore.MAX_ATTACHMENTS_PER_TASK,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathTraversalCannotEscapeAttachmentRoot() {
        AttachmentStore(temporaryFolder.root).fileFor("../outside")
    }

    @Test
    fun discardAndStagingCleanupAreIdempotent() {
        val store = AttachmentStore(temporaryFolder.root)
        val staged = store.stage(
            taskId = "task_1",
            source = ByteArrayInputStream("data".toByteArray()),
            displayName = "data.txt",
            mimeType = "text/plain",
        )
        store.discard(staged)
        store.discard(staged)
        assertTrue(store.cleanupStaging(olderThanMillis = 0) >= 0)
    }

    private class RepeatingInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            remaining--
            return 'x'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            Arrays.fill(buffer, offset, offset + count, 'x'.code.toByte())
            remaining -= count
            return count
        }
    }
}
