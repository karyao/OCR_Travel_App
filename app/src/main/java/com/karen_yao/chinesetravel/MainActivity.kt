package com.karen_yao.chinesetravel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependenciesOwner
import com.karen_yao.chinesetravel.features.home.ui.HomeDependenciesOwner
import com.karen_yao.chinesetravel.features.map.ui.MapDependenciesOwner
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionDependenciesOwner
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import com.karen_yao.chinesetravel.shared.ui.applyScreenInsets
import com.karen_yao.chinesetravel.shared.ui.configureScreenWindow

/**
 * MainActivity hosts a single container for fragments.
 * Supplies feature dependencies from the application composition root.
 */
internal class MainActivity : AppCompatActivity(), HomeDependenciesOwner, MapDependenciesOwner,
    CaptureDependenciesOwner, TextSelectionDependenciesOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreenWindow()
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.container).applyScreenInsets()

        supportActionBar?.hide()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, WelcomeFragment())
                .commit()
        }
    }

    override fun createHomeViewModelFactory() = appContainer.createHomeViewModelFactory()
    override fun createMapViewModelFactory() = appContainer.createMapViewModelFactory()
    override fun createCaptureDependencies() = appContainer.createCaptureDependencies()
    override fun createTextSelectionDependencies() = appContainer.createTextSelectionDependencies()
}
