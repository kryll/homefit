package com.kryll.homefit

import android.app.Application
import com.kryll.homefit.data.AppContainer

class HomeFitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
