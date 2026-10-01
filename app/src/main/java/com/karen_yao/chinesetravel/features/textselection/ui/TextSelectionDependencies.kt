package com.karen_yao.chinesetravel.features.textselection.ui

import com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal data class TextSelectionDependencies(
    val filesRoot: File,
    val saver: CapturedPlaceSaver,
    val cleanupDispatcher: CoroutineDispatcher = Dispatchers.IO
)

internal interface TextSelectionDependenciesOwner {
    fun createTextSelectionDependencies(): TextSelectionDependencies
}
