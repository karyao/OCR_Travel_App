package com.karen_yao.chinesetravel.features.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Map feature.
 * Provides the list of snaps that have valid GPS coordinates for pin placement.
 */
class MapViewModel(repository: TravelRepository) : ViewModel() {

    val snapsWithLocation: StateFlow<List<PlaceSnap>> = repository.getSnapsWithLocation()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

/**
 * Factory for creating MapViewModel instances with the shared TravelRepository.
 */
class MapViewModelFactory(private val repository: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
