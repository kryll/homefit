package com.kryll.homefit

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutCountdownSettingsTest {
    @Test
    fun cuentaAtrasDeAjustesLlegaAlMotor() {
        val item = com.kryll.homefit.domain.WorkItem("a", "A", 1, 10, null, 30)
        val r = com.kryll.homefit.audio.WorkoutHost.begin(77L, listOf(item), getReadySeconds = 9)
        assertEquals(9, r.snapshot.value.state.remaining)
        com.kryll.homefit.audio.WorkoutHost.clear()
        val r0 = com.kryll.homefit.audio.WorkoutHost.begin(78L, listOf(item), getReadySeconds = 0)
        assertEquals(com.kryll.homefit.domain.Phase.WORK, r0.snapshot.value.state.phase)
        com.kryll.homefit.audio.WorkoutHost.clear()
    }
}
