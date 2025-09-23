package com.karen_yao.chinesetravel.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.karen_yao.chinesetravel.data.model.PlaceSnap
import com.karen_yao.chinesetravel.data.repo.TravelRepository

class CaptureViewModel(private val repo: TravelRepository) : ViewModel() {

    suspend fun saveAndCount(
        ch: String,
        py: String,
        lat: Double?,
        lng: Double?,
        addr: String,
        path: String,
        trans: String
    ): Int {
        repo.saveSnap(
            PlaceSnap(
                imagePath = path,
                nameCn = ch,
                namePinyin = py,
                lat = lat,
                longitude = lng,
                address = addr,
                translation = trans
            )
        )
        return repo.count()
    }
}

class CaptureVMFactory(private val repo: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CaptureViewModel(repo) as T
}
