package com.karen_yao.chinesetravel.navigation

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment

internal interface TravelNavigatorOwner {
    val travelNavigator: TravelNavigator
}

/** Resolve the current host rather than retaining an activity across Fragment reattachment. */
internal val Fragment.travelNavigator: TravelNavigator
    @MainThread get() {
        val host = requireActivity() as? TravelNavigatorOwner
            ?: error("${javaClass.simpleName} host must provide travel navigation")
        return host.travelNavigator
    }
