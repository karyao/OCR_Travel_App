package com.karen_yao.chinesetravel.core.media

import java.io.File

/** Ownership of an unsaved managed image, shared by capture and text selection. */
internal class ManagedImageLease(
    val file: File,
    private val managedFilesRoot: File
) {
    private var persistenceStarted = false

    fun markPersistenceStarted() {
        persistenceStarted = true
    }

    fun discardIfSafe(): Boolean {
        if (persistenceStarted || !isManagedImage(file, managedFilesRoot)) return false
        return !file.exists() || file.delete()
    }
}

internal fun isManagedImage(file: File, managedFilesRoot: File): Boolean {
    val canonicalRoot = runCatching { managedFilesRoot.canonicalFile }.getOrNull() ?: return false
    val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
    return candidate.path.startsWith(canonicalRoot.path + File.separator)
}
