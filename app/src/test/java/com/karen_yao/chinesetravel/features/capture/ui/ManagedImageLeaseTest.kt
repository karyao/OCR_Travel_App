package com.karen_yao.chinesetravel.features.capture.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManagedImageLeaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun successfulPersistenceRetainsManagedImage() {
        val root = temporaryFolder.newFolder("files")
        val image = File(root, "capture.jpg").apply { writeText("image") }
        val lease = ManagedImageLease(image, root)

        lease.markPersistenceStarted()

        assertFalse(lease.discardIfSafe())
        assertTrue(image.exists())
    }

    @Test
    fun cancellationOrFailureBeforePersistenceDeletesManagedImage() {
        val root = temporaryFolder.newFolder("files")
        val cancelled = File(root, "cancelled.jpg").apply { writeText("image") }
        val failed = File(root, "failed.jpg").apply { writeText("image") }

        assertTrue(ManagedImageLease(cancelled, root).discardIfSafe())
        assertTrue(ManagedImageLease(failed, root).discardIfSafe())
        assertFalse(cancelled.exists())
        assertFalse(failed.exists())
    }

    @Test
    fun externalImageIsNeverDeleted() {
        val root = temporaryFolder.newFolder("files")
        val externalRoot = temporaryFolder.newFolder("external")
        val image = File(externalRoot, "external.jpg").apply { writeText("image") }

        assertFalse(ManagedImageLease(image, root).discardIfSafe())
        assertTrue(image.exists())
    }

    @Test
    fun similarlyPrefixedDirectoryIsNotManaged() {
        val parent = temporaryFolder.newFolder("parent")
        val root = File(parent, "files").apply { mkdirs() }
        val sibling = File(parent, "files-backup").apply { mkdirs() }
        val image = File(sibling, "capture.jpg").apply { writeText("image") }

        assertFalse(isManagedImage(image, root))
        assertFalse(ManagedImageLease(image, root).discardIfSafe())
        assertTrue(image.exists())
    }
}
