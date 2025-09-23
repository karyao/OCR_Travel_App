package com.karen_yao.chinesetravel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karen_yao.chinesetravel.data.repo.TravelRepository
import com.karen_yao.chinesetravel.data.model.PlaceSnap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repo: TravelRepository) : ViewModel() {
    val snaps: StateFlow<List<PlaceSnap>> =
        repo.snaps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
class HomeVMFactory(private val repo: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
}
