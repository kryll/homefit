package com.kryll.homefit

import android.app.Application
import com.kryll.homefit.data.AppContainer
import com.kryll.homefit.data.ExerciseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFitApp : Application() {
    /** Arranque asíncrono (semillas, canal, recordatorio); las pruebas lo esperan para no competir con él. */
    @Volatile var startup: kotlinx.coroutines.Job? = null
        private set
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        startup = CoroutineScope(Dispatchers.IO).launch { ExerciseSeeder.seedIfEmpty(this@HomeFitApp, container.database); com.kryll.homefit.data.RoutineSeeder.seedIfEmpty(this@HomeFitApp, container.database); com.kryll.homefit.reminders.ReminderNotifier.ensureChannel(this@HomeFitApp); com.kryll.homefit.reminders.ReminderScheduler.sync(this@HomeFitApp) }
    }
}
