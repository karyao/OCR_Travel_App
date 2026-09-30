package com.karen_yao.chinesetravel.shared.extensions

import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.core.repository.TravelRepositoryOwner
import com.karen_yao.chinesetravel.core.repository.TravelRepository

/**
 * Extension functions for Fragment classes.
 * Provides convenient access to shared resources.
 */

/**
 * Get the shared TravelRepository from the host.
 * This provides a convenient way for fragments to access the repository.
 */
fun Fragment.repo(): TravelRepository = (requireActivity() as TravelRepositoryOwner).repository
