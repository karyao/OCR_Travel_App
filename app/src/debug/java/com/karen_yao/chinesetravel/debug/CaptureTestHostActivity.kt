package com.karen_yao.chinesetravel.debug

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionDependencies
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionDependenciesOwner
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependencies
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependenciesOwner
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.appContainer
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import com.karen_yao.chinesetravel.features.home.ui.HomeDependenciesOwner
import com.karen_yao.chinesetravel.features.home.ui.HomeViewModelFactory
import com.karen_yao.chinesetravel.features.map.ui.MapDependenciesOwner
import com.karen_yao.chinesetravel.features.map.ui.MapViewModelFactory
import com.karen_yao.chinesetravel.shared.ui.applyScreenInsets
import com.karen_yao.chinesetravel.shared.ui.configureScreenWindow

/** Debug-only host that lets instrumentation tests replace device-facing capture services. */
internal class CaptureTestHostActivity : AppCompatActivity(), CaptureDependenciesOwner, TextSelectionDependenciesOwner, HomeDependenciesOwner, MapDependenciesOwner {
    lateinit var repository: TravelRepository
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreenWindow()
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.container).applyScreenInsets()
        supportActionBar?.hide()
        repository = appContainer.repository

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, initialFragmentFactory?.invoke() ?: CaptureFragment())
                .commitNow()
        }
    }

    override fun createCaptureDependencies(): CaptureDependencies =
        dependenciesFactory?.invoke(this, repository)
            ?: appContainer.createCaptureDependencies()

    override fun createTextSelectionDependencies(): TextSelectionDependencies =
        selectionDependenciesFactory?.invoke(this, repository)
            ?: appContainer.createTextSelectionDependencies()

    override fun createHomeViewModelFactory() = HomeViewModelFactory(repository)
    override fun createMapViewModelFactory() = MapViewModelFactory(mapSourceFactory?.invoke() ?: repository)

    companion object {
        internal var mapSourceFactory: (() -> LocatedSnapSource)? = null

        internal var selectionDependenciesFactory:
            ((Context, TravelRepository) -> TextSelectionDependencies)? = null

        internal var initialFragmentFactory: (() -> Fragment)? = null

        internal var dependenciesFactory:
            ((Context, TravelRepository) -> CaptureDependencies)? = null
    }
}
