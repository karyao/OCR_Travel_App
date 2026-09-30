package com.karen_yao.chinesetravel.features.textselection.ui

import android.content.Context
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
import com.karen_yao.chinesetravel.core.workflow.productionCapturedPlaceSaver
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

internal fun productionTextSelectionDependencies(
    context: Context,
    repository: TravelRepository
) = TextSelectionDependencies(
    filesRoot = context.applicationContext.filesDir,
    saver = productionCapturedPlaceSaver(context, repository)
)
