package com.kryll.homefit.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kryll.homefit.HomeFitApp
import com.kryll.homefit.MainActivity
import com.kryll.homefit.R
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.domain.Planning
import com.kryll.homefit.domain.ReminderDecision
import com.kryll.homefit.domain.ReminderPrefs
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun AppSettings.reminderPrefs() = ReminderPrefs(reminderEnabled, reminderHour, reminderMinute, reminderLeadMinutes, reminderExact)

object ReminderNotifier {
    const val CHANNEL_ID = "recordatorios"
    const val NOTIFICATION_ID = 2001
    const val SNOOZE_MINUTES = 15L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) },
        )
    }

    /** Publica el aviso. Devuelve false si el usuario tiene las notificaciones desactivadas (no se publica nada). */
    fun show(context: Context, routineName: String?): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (routineName != null) context.getString(R.string.reminder_text_routine, routineName)
        else context.getString(R.string.reminder_text)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                0, context.getString(R.string.reminder_snooze),
                PendingIntent.getBroadcast(
                    context, 1, Intent(context, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        @Suppress("MissingPermission") // areNotificationsEnabled() ya cubre POST_NOTIFICATIONS
        nm.notify(NOTIFICATION_ID, n)
        return true
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "recordatorio_diario"
    private const val SNOOZE_WORK = "recordatorio_posponer"
    const val KEY_SNOOZE = "snooze"

    /**
     * Programa el próximo aviso. Con [ReminderPrefs.exact] y el permiso de alarma concedido usa
     * AlarmManager.setExactAndAllowWhileIdle (puntual incluso con Doze; sin garantía con el móvil apagado
     * o la app detenida a la fuerza). Si no, cae al trabajo único de WorkManager, inexacto por diseño.
     * En ambos casos el aviso se reprograma a sí mismo tras disparar; BootReceiver lo realinea tras reiniciar.
     */
    fun schedule(context: Context, prefs: ReminderPrefs, daysMask: Int, from: ZonedDateTime = ZonedDateTime.now()) {
        val wm = WorkManager.getInstance(context)
        val next = if (prefs.enabled) Planning.nextReminder(from, daysMask, prefs) else null
        if (next == null) { wm.cancelUniqueWork(WORK_NAME); ExactReminders.cancel(context); return }
        if (prefs.exact && ExactReminders.canSchedule(context)) {
            wm.cancelUniqueWork(WORK_NAME)
            ExactReminders.scheduleAt(context, next.toInstant().toEpochMilli())
        } else {
            ExactReminders.cancel(context)
            val delay = Duration.between(ZonedDateTime.now(next.zone), next).toMillis().coerceAtLeast(0)
            wm.enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        }
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_NAME)
        wm.cancelUniqueWork(SNOOZE_WORK)
        ExactReminders.cancel(context)
    }

    /** Vuelve a avisar dentro de [ReminderNotifier.SNOOZE_MINUTES] minutos, sin tocar el aviso diario. */
    fun snooze(context: Context) {
        if (ExactReminders.canSchedule(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(SNOOZE_WORK)
            ExactReminders.scheduleAt(context, System.currentTimeMillis() + ReminderNotifier.SNOOZE_MINUTES * 60_000, snooze = true)
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            SNOOZE_WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(ReminderNotifier.SNOOZE_MINUTES, TimeUnit.MINUTES)
                .setInputData(androidx.work.workDataOf(KEY_SNOOZE to true)).build(),
        )
    }

    /** Alinea el trabajo con los ajustes y el perfil guardados (al abrir la app y al cambiar el recordatorio). */
    suspend fun sync(app: HomeFitApp) {
        val prefs = app.container.settings.settings.first().reminderPrefs()
        val profile = app.container.database.profileDao().profile()
        if (profile == null || !prefs.enabled) cancel(app) else schedule(app, prefs, profile.daysMask)
    }
}

/** Decide si avisar, avisa y reprograma el siguiente. Compartido por el Worker (WorkManager) y el Receiver (alarma exacta). */
object ReminderEngine {
    suspend fun runCycle(app: HomeFitApp, isSnooze: Boolean) {
        val db = app.container.database
        val prefs = app.container.settings.settings.first().reminderPrefs()
        val profile = db.profileDao().profile() ?: return
        if (!prefs.enabled) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val trained = db.sessionDao().workoutsBetween(from, to).any { it.notes != WorkoutRepository.NOTE_INCOMPLETE }
        val todays = db.sessionDao().scheduledBetween(from, to)
        if (Planning.decide(prefs, profile.daysMask, today, trained, todays.map { it.status }) == ReminderDecision.SHOW) {
            val routineId = todays.firstOrNull { it.status == "SCHEDULED" }?.routineId
            val name = routineId?.let { db.routineDao().byId(it)?.name }
            ReminderNotifier.show(app, name)
        }
        // Un aviso pospuesto no reprograma el diario, que sigue su propio mecanismo.
        if (isSnooze) return
        // Siguiente aviso: desde un minuto después de ahora, para no repetir el de hoy si el sistema lo adelantara.
        ReminderScheduler.schedule(app, prefs, profile.daysMask, ZonedDateTime.now(zone).plusMinutes(1))
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReminderEngine.runCycle(applicationContext as HomeFitApp, inputData.getBoolean(ReminderScheduler.KEY_SNOOZE, false))
        return Result.success()
    }
}

/** Alarmas exactas (SCHEDULE_EXACT_ALARM): dispara el mismo ciclo que el Worker. */
object ExactReminders {
    private const val RC_DAILY = 42
    private const val RC_SNOOZE = 43

    /** El permiso especial solo existe desde Android 12 (API 31); por debajo no hace falta. */
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Intent de ajustes del sistema para que el usuario conceda «Alarmas y recordatorios». */
    fun permissionIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < 31 || canSchedule(context)) null
        else Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))

    private fun pendingIntent(context: Context, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, if (snooze) RC_SNOOZE else RC_DAILY,
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderScheduler.KEY_SNOOZE, snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Programa una alarma exacta que despierta el dispositivo. No hace nada sin el permiso. */
    fun scheduleAt(context: Context, triggerAtMillis: Long, snooze: Boolean = false) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!canSchedule(context)) return
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, snooze))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, false))
        am.cancel(pendingIntent(context, true))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderEngine.runCycle(context.applicationContext as HomeFitApp, intent.getBooleanExtra(ReminderScheduler.KEY_SNOOZE, false))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Las alarmas exactas no sobreviven al reinicio: realinea tras arrancar o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler.sync(context.applicationContext as HomeFitApp)
            } finally {
                pending.finish()
            }
        }
    }
}
