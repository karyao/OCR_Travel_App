package com.karen_yao.chinesetravel.features.home.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.SnapRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class HomeUiState(
    val snaps: List<PlaceSnap> = emptyList(),
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val loadFailed: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && !loadFailed && snaps.isEmpty()

    val snapCount: Int
        get() = snaps.size
}

sealed interface HomeEffect {
    data class ShowMessage(@StringRes val message: Int) : HomeEffect
}

/**
 * ViewModel for the Home feature.
 * Manages the list of captured place snaps.
 */
class HomeViewModel(private val repository: SnapRepository) : ViewModel() {

    private val isMutating = MutableStateFlow(false)
    private val mutationGuard = AtomicBoolean(false)
    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)

    val effects = effectChannel.receiveAsFlow()

    private val collectionState = repository.getAllSnaps()
        .map { snaps ->
            HomeUiState(
                snaps = snaps,
                isLoading = false
            )
        }
        .catch {
            emit(
                HomeUiState(
                    isLoading = false,
                    loadFailed = true
                )
            )
        }

    val uiState: StateFlow<HomeUiState> = combine(collectionState, isMutating) { state, mutating ->
        state.copy(isMutating = mutating)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    fun deleteSnap(snap: PlaceSnap) {
        mutate(
            successMessage = R.string.home_delete_success,
            errorMessage = R.string.home_delete_error
        ) {
            repository.deleteSnap(snap)
        }
    }

    fun clearAllSnaps() {
        mutate(
            successMessage = R.string.home_clear_success,
            errorMessage = R.string.home_clear_error
        ) {
            repository.clearAllSnaps()
        }
    }

    private fun mutate(
        @StringRes successMessage: Int,
        @StringRes errorMessage: Int,
        operation: suspend () -> Unit
    ) {
        if (!mutationGuard.compareAndSet(false, true)) return

        isMutating.value = true
        viewModelScope.launch {
            try {
                operation()
                effectChannel.send(HomeEffect.ShowMessage(successMessage))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                effectChannel.send(HomeEffect.ShowMessage(errorMessage))
            } finally {
                isMutating.value = false
                mutationGuard.set(false)
            }
        }
    }
}

/**
 * Factory for creating HomeViewModel instances.
 */
class HomeViewModelFactory(private val repository: SnapRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(repository) as T
    }
}
