package com.karen_yao.chinesetravel.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.data.model.PlaceSnap
import com.karen_yao.chinesetravel.data.repo.TravelRepository
import kotlinx.coroutines.launch

/** Handles saving PlaceSnap to the database (business logic). */
class CaptureViewModel(private val repo: TravelRepository) : ViewModel() {
    fun save(ch: String, py: String, lat: Double?, lng: Double?, addr: String?, path: String) {
        viewModelScope.launch {
            repo.saveSnap(
                PlaceSnap(
                    imagePath = path,
                    nameCn = ch,
                    namePinyin = py,
                    lat = lat,
                    longitude = lng,
                    address = addr
                )
            )
        }
    }
}

class CaptureVMFactory(private val repo: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CaptureViewModel(repo) as T
}
