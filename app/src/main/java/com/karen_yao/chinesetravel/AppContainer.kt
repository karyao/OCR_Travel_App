package com.karen_yao.chinesetravel

import android.content.Context
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.media.AndroidPlaceMetadata
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.core.workflow.CapturePersistence
import com.karen_yao.chinesetravel.core.workflow.DefaultCapturedPlaceSaver
import com.karen_yao.chinesetravel.features.capture.ui.productionCaptureDependencies
import com.karen_yao.chinesetravel.features.home.ui.HomeViewModelFactory
import com.karen_yao.chinesetravel.features.map.ui.MapViewModelFactory
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionDependencies
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils

/** Composition root: connects feature contracts to Android and Room implementations. */
internal class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val repository by lazy { TravelRepository(AppDatabase.getDatabase(applicationContext)) }
    private val saver by lazy {
        val metadata = AndroidPlaceMetadata(applicationContext)
        DefaultCapturedPlaceSaver(
            persistence = CapturePersistence(repository, TranslationUtils::translateChineseToEnglish),
            toPinyin = PinyinUtils::toPinyin,
            readLocation = metadata::readLocation,
            reverseGeocode = metadata::reverseGeocode
        )
    }

    fun createHomeViewModelFactory() = HomeViewModelFactory(repository)
    fun createMapViewModelFactory() = MapViewModelFactory(repository)
    // Camera bindings belong to a screen; OCR is created once per capture ViewModel.
    fun createCaptureDependencies() = productionCaptureDependencies(applicationContext, saver)
    fun createTextSelectionDependencies() = TextSelectionDependencies(applicationContext.filesDir, saver)
}
