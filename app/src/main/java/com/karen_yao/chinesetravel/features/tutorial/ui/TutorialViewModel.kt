package com.karen_yao.chinesetravel.features.tutorial.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.karen_yao.chinesetravel.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TutorialStep(@StringRes val title: Int, @StringRes val description: Int, val imageAsset: String)

internal data class TutorialUiState(val index: Int, val step: TutorialStep, val totalSteps: Int) {
    val canGoPrevious: Boolean get() = index > 0
    val canGoNext: Boolean get() = index < totalSteps - 1
}

/** Keeps tutorial progress across view recreation and saved-state restoration. */
internal class TutorialViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val steps = listOf(
        TutorialStep(R.string.tutorial_welcome_title, R.string.tutorial_welcome_description, "Travel_Logo.png"),
        TutorialStep(R.string.tutorial_capture_title, R.string.tutorial_capture_description, "IMG_3950.JPG"),
        TutorialStep(R.string.tutorial_translation_title, R.string.tutorial_translation_description, "TranslationExample.png"),
        TutorialStep(R.string.tutorial_location_title, R.string.tutorial_location_description, "LocationExample.png")
    )
    private val initialIndex = (savedState.get<Int>(STEP_INDEX) ?: 0).coerceIn(steps.indices)
    private val mutableState = MutableStateFlow(state(initialIndex))
    val uiState = mutableState.asStateFlow()

    init { savedState[STEP_INDEX] = initialIndex }

    fun previous() = moveTo(uiState.value.index - 1)
    fun next() = moveTo(uiState.value.index + 1)

    private fun moveTo(index: Int) {
        if (index !in steps.indices) return
        savedState[STEP_INDEX] = index
        mutableState.value = state(index)
    }

    private fun state(index: Int) = TutorialUiState(index, steps[index], steps.size)

    internal companion object { const val STEP_INDEX = "tutorial.step_index" }
}
