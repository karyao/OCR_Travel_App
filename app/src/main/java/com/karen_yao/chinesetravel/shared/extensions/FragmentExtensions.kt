package com.karen_yao.chinesetravel.shared.extensions

import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.core.repository.TravelRepository

/**
 * Extension functions for Fragment classes.
 * Provides convenient access to shared resources.
 */

/**
 * Get the shared TravelRepository from MainActivity.
 * This provides a convenient way for fragments to access the repository.
 */
fun Fragment.repo(): TravelRepository = (requireActivity() as MainActivity).repository
