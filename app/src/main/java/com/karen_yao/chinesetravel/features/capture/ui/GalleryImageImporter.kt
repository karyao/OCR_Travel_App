package com.karen_yao.chinesetravel.features.capture.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

internal class GalleryImageImporter(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val createDestination: (File) -> File = { directory ->
        File.createTempFile("gal_", ".jpg", directory)
    }
) {
    suspend fun importImage(
        importDirectory: File,
        openSource: () -> InputStream?
    ): File {
        var destination: File? = null
        try {
            return withContext(dispatcher) {
                check(importDirectory.exists() || importDirectory.mkdirs()) {
                    "Could not create the gallery import directory"
                }
                val file = createDestination(importDirectory).also { destination = it }
                openSource().use { source ->
                    checkNotNull(source) { "The selected image could not be opened" }
                    file.outputStream().use(source::copyTo)
                }
                ensureActive()
                check(file.length() > 0L) { "The selected image was empty" }
                file
            }
        } catch (exception: Exception) {
            // Includes cancellation delivered when withContext returns to the caller.
            destination?.delete()
            throw exception
        }
    }
}
