package com.karen_yao.chinesetravel.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Home feature.
 * Manages the list of captured place snaps.
 */
class HomeViewModel(repository: TravelRepository) : ViewModel() {
    
    val snaps: StateFlow<List<PlaceSnap>> = repository.getAllSnaps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

/**
 * Factory for creating HomeViewModel instances.
 */
class HomeViewModelFactory(private val repository: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(repository) as T
    }
}
