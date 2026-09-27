package com.karen_yao.chinesetravel.debug

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependencies
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependenciesOwner
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.capture.ui.productionCaptureDependencies

/** Debug-only host that lets instrumentation tests replace device-facing capture services. */
internal class CaptureTestHostActivity : AppCompatActivity(), CaptureDependenciesOwner {
    private lateinit var repository: TravelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        repository = TravelRepository(AppDatabase.getDatabase(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .commitNow()
        }
    }

    override fun createCaptureDependencies(): CaptureDependencies =
        dependenciesFactory?.invoke(this, repository)
            ?: productionCaptureDependencies(this, repository)

    companion object {
        internal var dependenciesFactory:
            ((Context, TravelRepository) -> CaptureDependencies)? = null
    }
}
