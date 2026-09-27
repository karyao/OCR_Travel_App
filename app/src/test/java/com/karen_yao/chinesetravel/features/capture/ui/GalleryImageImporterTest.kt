package com.karen_yao.chinesetravel.features.capture.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GalleryImageImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun successfulImportProducesNonEmptyManagedFile() = runBlocking {
        val directory = temporaryFolder.newFolder("imports")
        val bytes = byteArrayOf(1, 2, 3, 4)

        val result = GalleryImageImporter().importImage(directory) {
            ByteArrayInputStream(bytes)
        }

        assertArrayEquals(bytes, result.readBytes())
        assertFalse(result.length() == 0L)
    }

    @Test
    fun sourceFailureRemovesPartialDestination() = runBlocking {
        val directory = temporaryFolder.newFolder("imports")
        val destination = File(directory, "partial.jpg")
        val importer = GalleryImageImporter { destination.apply { writeText("partial") } }

        try {
            importer.importImage(directory) { throw IllegalStateException("cannot open") }
            fail("Expected import failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertFalse(destination.exists())
    }

    @Test
    fun copyFailureRemovesPartiallyWrittenDestination() = runBlocking {
        val directory = temporaryFolder.newFolder("imports")
        val destination = File(directory, "copy-failure.jpg")
        val importer = GalleryImageImporter { destination }
        val failingSource = object : InputStream() {
            private var firstByteRead = false

            override fun read(): Int {
                if (!firstByteRead) {
                    firstByteRead = true
                    return 1
                }
                throw IOException("copy failed")
            }
        }

        try {
            importer.importImage(directory) { failingSource }
            fail("Expected copy failure")
        } catch (_: IOException) {
            // Expected.
        }

        assertFalse(destination.exists())
    }

    @Test
    fun cancellationRemovesPartialDestinationAndIsRethrown() = runBlocking {
        val directory = temporaryFolder.newFolder("imports")
        val destination = File(directory, "cancelled.jpg")
        val importer = GalleryImageImporter { destination.apply { writeText("partial") } }

        try {
            importer.importImage(directory) { throw CancellationException("cancelled") }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }

        assertFalse(destination.exists())
    }
}
