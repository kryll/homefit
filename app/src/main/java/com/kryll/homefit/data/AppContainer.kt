package com.kryll.homefit.data

import android.content.Context

/** Inyección manual de dependencias: una instancia por proceso, creada en [com.kryll.homefit.HomeFitApp]. */
class AppContainer(context: Context) {
    val database: HomeFitDatabase = HomeFitDatabase.build(context)
    val settings: SettingsStore = SettingsStore(context)
    /** Único por proceso: su cerrojo ordena los puntos de control del servicio y el guardado final. */
    val workoutRepository: WorkoutRepository = WorkoutRepository(database)
}
