package com.karen_yao.chinesetravel.features.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal interface MapDependenciesOwner {
    fun createMapViewModelFactory(): ViewModelProvider.Factory
}

internal data class MapPin(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String
)

internal data class MapUiState(
    val pins: List<MapPin> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && !loadFailed && pins.isEmpty()
}

/** Owns data loading and marker content; map widgets stay in the Fragment. */
internal class MapViewModel(source: LocatedSnapSource) : ViewModel() {
    val uiState: StateFlow<MapUiState> = source.getSnapsWithLocation()
        .map { snaps -> MapUiState(pins = snaps.mapNotNull(::toMapPin), isLoading = false) }
        .catch { emit(MapUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapUiState())

    private fun toMapPin(snap: PlaceSnap): MapPin? {
        val latitude = snap.lat ?: return null
        val longitude = snap.longitude ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val snippet = listOfNotNull(
            snap.namePinyin.takeIf { it.isNotBlank() }?.let { "📖 $it" },
            snap.translation.takeIf { it.isNotBlank() }?.let { "🌐 $it" },
            snap.address?.takeIf { it.isNotBlank() }?.let { "📍 $it" }
        ).joinToString("\n")
        return MapPin(latitude, longitude, snap.nameCn, snippet)
    }
}

internal class MapViewModelFactory(private val source: LocatedSnapSource) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return MapViewModel(source) as T
    }
}
