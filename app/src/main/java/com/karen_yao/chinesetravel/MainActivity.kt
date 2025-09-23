package com.karen_yao.chinesetravel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.data.db.AppDatabase
import com.karen_yao.chinesetravel.data.repo.TravelRepository
import com.karen_yao.chinesetravel.ui.home.HomeFragment

/**
 * MainActivity hosts a single container for fragments.
 * It creates one TravelRepository (Room DB) and exposes it to fragments.
 */
class MainActivity : AppCompatActivity() {
    lateinit var repo: TravelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Build Room database + Repository
        repo = TravelRepository(AppDatabase.get(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commit()
        }
    }
}

/** Convenience extension so fragments can access the shared repository. */
fun Fragment.repo(): TravelRepository = (requireActivity() as MainActivity).repo
