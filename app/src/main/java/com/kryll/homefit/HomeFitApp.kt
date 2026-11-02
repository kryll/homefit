package com.kryll.homefit

import android.app.Application
import com.kryll.homefit.data.AppContainer
import com.kryll.homefit.data.ExerciseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(Dispatchers.IO).launch { ExerciseSeeder.seedIfEmpty(this@HomeFitApp, container.database) }
    }
}
