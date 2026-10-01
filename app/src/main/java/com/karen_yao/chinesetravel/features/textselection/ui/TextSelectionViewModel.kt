package com.karen_yao.chinesetravel.features.textselection.ui

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.karen_yao.chinesetravel.core.media.ManagedImageLease
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class TextSelectionStatus { READY, SAVING, SAVED, OUTCOME_UNKNOWN, INVALID_INPUT, EXITING }

internal sealed interface TextSelectionEffect {
    data object ShowSaveError : TextSelectionEffect
    data object ShowInvalidInput : TextSelectionEffect
    data class SavedAndGoHome(val count: Int) : TextSelectionEffect
    data object GoBack : TextSelectionEffect
}

internal data class TextSelectionCommand(val id: Long, val effect: TextSelectionEffect)

internal data class TextSelectionUiState(
    val lines: List<String>,
    val imagePath: String,
    val selectedIndex: Int,
    val status: TextSelectionStatus,
    val commands: List<TextSelectionCommand> = emptyList()
) {
    val selectionEnabled: Boolean get() = status == TextSelectionStatus.READY
    val confirmEnabled: Boolean get() = selectionEnabled &&
        selectedIndex in lines.indices && lines[selectedIndex].isNotBlank()
    val exitEnabled: Boolean get() = status == TextSelectionStatus.READY ||
        status == TextSelectionStatus.OUTCOME_UNKNOWN || status == TextSelectionStatus.INVALID_INPUT
}

/** Owns selection, one save attempt, and only the image explicitly handed off by capture. */
internal class TextSelectionViewModel(
    private val savedState: SavedStateHandle,
    private val services: TextSelectionDependencies
) : ViewModel() {
    private val lines = savedState.get<Array<String>>(ARG_LINES)?.toList().orEmpty()
    private val imagePath = savedState.get<String>(ARG_IMAGE_PATH).orEmpty()
    private val lease = if (savedState.get<Boolean>(ARG_OWNED) == true && imagePath.isNotBlank()) {
        ManagedImageLease(File(imagePath), services.filesRoot)
    } else null
    private var nextCommand = 0L
    private var cleanupScheduled = false
    private val mutableState: MutableStateFlow<TextSelectionUiState>
    val uiState: StateFlow<TextSelectionUiState>

    init {
        val selected = (savedState.get<Int>(SELECTED_INDEX)
            ?: lines.indexOf(savedState.get<String>(ARG_SELECTED_TEXT)))
            .takeIf { it in lines.indices } ?: -1
        var status = savedState.get<String>(STATUS)?.let { stored ->
            TextSelectionStatus.entries.firstOrNull { it.name == stored }
        } ?: TextSelectionStatus.READY
        // Saved state is a checkpoint, not a durable record of database commits. Never
        // resume work automatically in a new ViewModel after an interrupted attempt.
        if (status == TextSelectionStatus.SAVING) {
            status = TextSelectionStatus.OUTCOME_UNKNOWN
            savedState[MESSAGE_PENDING] = true
        }
        if (status == TextSelectionStatus.READY &&
            (imagePath.isBlank() || lines.none { it.isNotBlank() })) {
            status = TextSelectionStatus.INVALID_INPUT
            savedState[MESSAGE_PENDING] = true
        }
        if (savedState.get<Boolean>(SAVE_ATTEMPTED) == true) lease?.markPersistenceStarted()
        savedState[STATUS] = status.name
        val commands = when {
            status == TextSelectionStatus.SAVED && savedState.get<Boolean>(NAVIGATION_PENDING) != false ->
                listOf(command(TextSelectionEffect.SavedAndGoHome(savedState[COUNT] ?: 0)))
            status == TextSelectionStatus.EXITING && savedState.get<Boolean>(NAVIGATION_PENDING) != false ->
                listOf(command(TextSelectionEffect.GoBack))
            status == TextSelectionStatus.OUTCOME_UNKNOWN && savedState.get<Boolean>(MESSAGE_PENDING) == true ->
                listOf(command(TextSelectionEffect.ShowSaveError))
            status == TextSelectionStatus.INVALID_INPUT && savedState.get<Boolean>(MESSAGE_PENDING) == true ->
                listOf(command(TextSelectionEffect.ShowInvalidInput))
            else -> emptyList()
        }
        mutableState = MutableStateFlow(TextSelectionUiState(lines, imagePath, selected, status, commands))
        uiState = mutableState.asStateFlow()
    }

    @MainThread
    fun select(index: Int) {
        if (!uiState.value.selectionEnabled || index !in lines.indices || lines[index].isBlank()) return
        savedState[SELECTED_INDEX] = index
        mutableState.value = uiState.value.copy(selectedIndex = index)
    }

    @MainThread
    fun confirm() {
        val state = uiState.value
        if (!state.confirmEnabled) return
        // Lock synchronously before launch, including while the dispatcher is busy.
        savedState[SAVE_ATTEMPTED] = true
        lease?.markPersistenceStarted()
        setStatus(TextSelectionStatus.SAVING)
        viewModelScope.launch {
            try {
                val count = services.saver.save(lines[state.selectedIndex], File(imagePath))
                kotlin.coroutines.coroutineContext.ensureActive()
                savedState[COUNT] = count
                savedState[NAVIGATION_PENDING] = true
                setStatus(TextSelectionStatus.SAVED)
                enqueue(TextSelectionEffect.SavedAndGoHome(count))
            } catch (cancelled: CancellationException) {
                // Cancellation may arrive after commit. Retain the file and prevent retry.
                saveOutcomeUnknown()
                throw cancelled
            } catch (_: Exception) {
                saveOutcomeUnknown()
            }
        }
    }

    @MainThread
    fun cancel() {
        if (!uiState.value.exitEnabled) return
        savedState[NAVIGATION_PENDING] = true
        setStatus(TextSelectionStatus.EXITING)
        cleanup()
        // An unshown error should not delay a requested exit.
        mutableState.value = uiState.value.copy(commands = listOf(command(TextSelectionEffect.GoBack)))
    }

    @MainThread
    fun acknowledge(id: Long) {
        val head = uiState.value.commands.firstOrNull() ?: return
        if (head.id != id) return
        when (head.effect) {
            TextSelectionEffect.ShowSaveError, TextSelectionEffect.ShowInvalidInput -> savedState[MESSAGE_PENDING] = false
            else -> savedState[NAVIGATION_PENDING] = false
        }
        mutableState.value = uiState.value.copy(commands = uiState.value.commands.drop(1))
    }

    private fun saveOutcomeUnknown() {
        savedState[MESSAGE_PENDING] = true
        setStatus(TextSelectionStatus.OUTCOME_UNKNOWN)
        enqueue(TextSelectionEffect.ShowSaveError)
    }

    private fun command(effect: TextSelectionEffect) = TextSelectionCommand(++nextCommand, effect)
    private fun enqueue(effect: TextSelectionEffect) {
        mutableState.value = uiState.value.copy(commands = uiState.value.commands + command(effect))
    }
    private fun setStatus(status: TextSelectionStatus) {
        savedState[STATUS] = status.name
        mutableState.value = uiState.value.copy(status = status)
    }
    private fun cleanup() {
        if (cleanupScheduled || lease == null || savedState.get<Boolean>(SAVE_ATTEMPTED) == true) return
        cleanupScheduled = true
        // Cleanup must finish even after ViewModel removal cancels its normal scope.
        viewModelScope.launch(NonCancellable + services.cleanupDispatcher) { lease.discardIfSafe() }
    }
    override fun onCleared() { cleanup() }

    internal companion object {
        const val ARG_LINES = "detected_texts"
        const val ARG_IMAGE_PATH = "image_path"
        const val ARG_SELECTED_TEXT = "selected_text"
        const val ARG_OWNED = "delete_image_if_unsaved"
        const val SELECTED_INDEX = "selection.index"
        const val STATUS = "selection.status"
        const val SAVE_ATTEMPTED = "selection.save_attempted"
        const val COUNT = "selection.saved_count"
        const val NAVIGATION_PENDING = "selection.navigation_pending"
        const val MESSAGE_PENDING = "selection.message_pending"
    }
}

internal class TextSelectionViewModelFactory(
    private val createServices: () -> TextSelectionDependencies
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(TextSelectionViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return TextSelectionViewModel(extras.createSavedStateHandle(), createServices()) as T
    }
}
