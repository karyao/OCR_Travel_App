package com.karen_yao.chinesetravel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.core.repository.TravelRepositoryOwner
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependencies
import com.karen_yao.chinesetravel.features.capture.ui.productionCaptureDependencies
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import com.karen_yao.chinesetravel.shared.ui.applyScreenInsets
import com.karen_yao.chinesetravel.shared.ui.configureScreenWindow

/**
 * MainActivity hosts a single container for fragments.
 * It creates one TravelRepository (Room DB) and exposes it to fragments.
 */
class MainActivity : AppCompatActivity(), TravelRepositoryOwner {
    
    override lateinit var repository: TravelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreenWindow()
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.container).applyScreenInsets()

        supportActionBar?.hide()
        // Build Room database + Repository
        repository = TravelRepository(AppDatabase.getDatabase(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, WelcomeFragment())
                .commit()
        }
    }

    internal fun createCaptureDependencies(): CaptureDependencies =
        productionCaptureDependencies(this, repository)
}

/**
 * Convenience extension so fragments can access the shared repository.
 * @deprecated Use Fragment.repo() extension instead
 */
@Deprecated("Use Fragment.repo() extension instead")
fun Fragment.repo(): TravelRepository = (requireActivity() as TravelRepositoryOwner).repository
