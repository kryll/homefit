package com.kryll.homefit.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Botón "Posponer" del aviso: quita el aviso y lo repite pasados unos minutos si sigue sin entrenar. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        context.getSystemService(NotificationManager::class.java).cancel(ReminderNotifier.NOTIFICATION_ID)
        ReminderScheduler.snooze(context)
    }

    companion object { const val ACTION = "com.kryll.homefit.action.SNOOZE_REMINDER" }
}
