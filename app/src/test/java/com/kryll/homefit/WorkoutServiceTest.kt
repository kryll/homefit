package com.kryll.homefit

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryll.homefit.audio.WorkoutService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import android.content.Intent

@RunWith(AndroidJUnit4::class)
class WorkoutServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun notificacionEsPersistenteYUsaElTitulo() {
        val n = WorkoutService.buildNotification(context, "Fuerza 20 min")
        assertEquals("Fuerza 20 min", n.extras.getCharSequence("android.title").toString())
        assertTrue(n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(n.contentIntent)
    }

    @Test
    fun canalCreadoConImportanciaBaja() {
        WorkoutService.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(WorkoutService.CHANNEL_ID).importance)
    }

    @Test
    fun servicioArrancaEnPrimerPlanoYSePara() {
        val controller = Robolectric.buildService(WorkoutService::class.java, Intent(context, WorkoutService::class.java))
        val service = controller.create().startCommand(0, 1).get()
        val shadow = org.robolectric.Shadows.shadowOf(service)
        assertNotNull(shadow.lastForegroundNotification)
        controller.destroy()
    }

    @Test
    fun notificacionTieneAccionesPausarYTerminar() {
        val n = WorkoutService.buildNotification(context, null, paused = false)
        assertEquals(listOf("Pausar", "Siguiente", "Terminar"), n.actions.map { it.title.toString() })
        val p = WorkoutService.buildNotification(context, null, paused = true)
        assertEquals(listOf("Reanudar", "Siguiente", "Terminar"), p.actions.map { it.title.toString() })
    }

    @Test
    fun accionPausarAlternaElRunner() {
        val runner = com.kryll.homefit.audio.WorkoutHost.begin(
            42L, listOf(com.kryll.homefit.domain.WorkItem("a", "A", 1, 10, null, 30)),
        )
        val toggle = Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_TOGGLE)
        val controller = Robolectric.buildService(WorkoutService::class.java, toggle)
        controller.create().startCommand(0, 1)
        assertTrue(runner.snapshot.value.state.paused)
        controller.startCommand(0, 2)
        assertEquals(false, runner.snapshot.value.state.paused)
        runner.exit()
        com.kryll.homefit.audio.WorkoutHost.clear()
        controller.destroy()
    }

    @Test fun siguienteAvanzaFaseSinInventarResultados() {
        val runner = com.kryll.homefit.audio.WorkoutHost.begin(
            99L, listOf(com.kryll.homefit.domain.WorkItem("a", "A", 2, 10, null, 30)),
        )
        val next = Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_NEXT)
        val controller = Robolectric.buildService(WorkoutService::class.java, next)
        controller.create().startCommand(0, 1)
        assertEquals(com.kryll.homefit.domain.Phase.WORK, runner.snapshot.value.state.phase)
        assertTrue(runner.results.isEmpty())
        controller.startCommand(0, 2)
        assertTrue(runner.results.single().skipped)
        runner.exit()
        com.kryll.homefit.audio.WorkoutHost.clear()
        controller.destroy()
    }
}
