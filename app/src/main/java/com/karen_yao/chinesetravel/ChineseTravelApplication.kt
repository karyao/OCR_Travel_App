package com.karen_yao.chinesetravel

import android.app.Application
import android.content.Context

/** Owns application-scoped dependencies without retaining an Activity or its views. */
class ChineseTravelApplication : Application() {
    internal val container by lazy { AppContainer(this) }
}

internal val Context.appContainer: AppContainer
    get() = (applicationContext as ChineseTravelApplication).container
