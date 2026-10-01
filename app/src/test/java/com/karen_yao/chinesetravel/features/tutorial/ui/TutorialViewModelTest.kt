package com.karen_yao.chinesetravel.features.tutorial.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.junit.Assert.*

class TutorialViewModelTest {
    @Test fun initialStepAndNavigationRespectBothBoundaries() {
        val model = TutorialViewModel(SavedStateHandle())
        model.previous()
        assertEquals(0, model.uiState.value.index)
        assertFalse(model.uiState.value.canGoPrevious)
        repeat(10) { model.next() }
        assertEquals(3, model.uiState.value.index)
        assertFalse(model.uiState.value.canGoNext)
        repeat(10) { model.previous() }
        assertEquals(0, model.uiState.value.index)
    }

    @Test fun recreatedViewModelRestoresTheSelectedStep() {
        val savedState = SavedStateHandle()
        val first = TutorialViewModel(savedState)
        repeat(2) { first.next() }
        val restored = TutorialViewModel(SavedStateHandle(mapOf(
            TutorialViewModel.STEP_INDEX to savedState.get<Int>(TutorialViewModel.STEP_INDEX)
        )))
        assertEquals(first.uiState.value, restored.uiState.value)
        restored.previous()
        assertEquals(1, restored.uiState.value.index)
    }

    @Test fun outOfRangeRestoredStepIsClamped() {
        for ((stored, expected) in listOf(-1 to 0, 100 to 3)) {
            val state = SavedStateHandle(mapOf(TutorialViewModel.STEP_INDEX to stored))
            val model = TutorialViewModel(state)
            assertEquals(expected, model.uiState.value.index)
            assertEquals(expected, state.get<Int>(TutorialViewModel.STEP_INDEX))
        }
    }
}
