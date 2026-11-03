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
}
