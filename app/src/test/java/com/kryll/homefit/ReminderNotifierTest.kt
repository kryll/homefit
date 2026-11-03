package com.kryll.homefit

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.reminders.ReminderNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Test fun postsNotificationInItsOwnChannelWithRoutineName() {
        assertTrue(ReminderNotifier.show(context, "Fuerza en casa"))
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
        assertEquals(ReminderNotifier.CHANNEL_ID, posted[0].channelId)
        assertEquals("Hoy toca entrenar", posted[0].extras.getString("android.title"))
        assertEquals("Tienes planificada: Fuerza en casa.", posted[0].extras.getString("android.text"))
        assertTrue(nm.getNotificationChannel(ReminderNotifier.CHANNEL_ID) != null)
    }

    @Test fun genericTextWithoutRoutine() {
        assertTrue(ReminderNotifier.show(context, null))
        assertEquals("Abre HomeFit y elige tu rutina.", shadowOf(nm).allNotifications[0].extras.getString("android.text"))
    }

    @Test fun postsNothingIfNotificationsAreDisabled() {
        shadowOf(nm).setNotificationsEnabled(false)
        assertFalse(ReminderNotifier.show(context, null))
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    @Test fun notificationOffersSnoozeAction() {
        assertTrue(ReminderNotifier.show(context, null))
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(1, n.actions.size)
        assertEquals("Posponer 15 min", n.actions[0].title.toString())
    }
}
