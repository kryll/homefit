package com.kryll.homefit.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kryll.homefit.MainActivity
import com.kryll.homefit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano que mantiene vivo el proceso mientras hay un entreno en marcha,
 * para que el reloj y los avisos de voz sigan con la pantalla apagada.
 * El motor sigue en la pantalla de entreno; el servicio solo da prioridad de proceso, notificación y wake lock parcial.
 */
class WorkoutService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var checkpoints: Job? = null
    private var player: CuePlayer? = null
    /** Aparte de [scope] para que el guardado final no se cancele al parar el servicio. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val notification = buildNotification(this, intent?.getStringExtra(EXTRA_TITLE), WorkoutHost.runner?.snapshot?.value?.state?.paused == true)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homefit:workout").apply {
                acquire(MAX_WAKE_MS)
            }
        }
        val runner = WorkoutHost.runner
        if (runner == null || runner.isClosed) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> runner.togglePause()
            ACTION_NEXT -> runner.skip()
            ACTION_FINISH -> { finishFromNotification(runner); return START_NOT_STICKY }
        }
        if (loop == null) {
            val p = CuePlayer(applicationContext).also { player = it }
            runner.cueListener = { p.play(it) }
            val repo = (application as com.kryll.homefit.HomeFitApp).container.workoutRepository
            val start = WorkoutHost.startMillis
            val routine = WorkoutHost.routineId
            // Punto de control tras cada serie y cada RPE: si el proceso muere, el entreno queda guardado como incompleto.
            checkpoints = scope.launch {
                runner.snapshot.map { it.completedSets * 10 + it.state.phase.ordinal }.distinctUntilChanged().collect {
                    repo.checkpoint(routine, runner.items, start, System.currentTimeMillis(), runner.results) { runner.isClosed }
                }
            }
            // Actualiza la notificación (Pausar/Reanudar) cuando cambia el estado de pausa.
            scope.launch {
                runner.snapshot.map { it.state.paused }.distinctUntilChanged().collect { paused ->
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(this@WorkoutService, null, paused))
                }
            }
            loop = scope.launch {
                // El reloj vive aquí, no en la pantalla: sigue con la pantalla apagada.
                while (!runner.isClosed && !runner.isFinished) {
                    runner.sync()
                    delay(TICK_MS)
                }
                if (!runner.isClosed) {
                    delay(FINISH_GRACE_MS) // deja terminar el aviso de fin
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Termina desde la notificación: cierra la sesión una sola vez, la guarda y para el servicio. */
    private fun finishFromNotification(runner: com.kryll.homefit.domain.WorkoutRunner) {
        val repo = (application as com.kryll.homefit.HomeFitApp).container.workoutRepository
        val start = WorkoutHost.startMillis
        val routine = WorkoutHost.routineId
        val r = runner.exit()
        saveScope.launch {
            if (r != null) {
                repo.save(routine, runner.items, start, System.currentTimeMillis(), r.second, r.first)
                WorkoutHost.clear()
            }
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        WorkoutHost.runner?.cueListener = null
        player?.release()
        player = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.kryll.homefit.action.STOP_WORKOUT"
        const val ACTION_TOGGLE = "com.kryll.homefit.action.TOGGLE_PAUSE"
        const val ACTION_NEXT = "com.kryll.homefit.action.NEXT_WORKOUT"
        const val ACTION_FINISH = "com.kryll.homefit.action.FINISH_WORKOUT"
        const val EXTRA_TITLE = "title"
        /** Tope de seguridad: 3 horas. */
        const val TICK_MS = 250L
        const val FINISH_GRACE_MS = 6000L
        const val MAX_WAKE_MS = 3L * 60 * 60 * 1000

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.workout_channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        private fun action(context: Context, code: Int, act: String): PendingIntent =
            PendingIntent.getService(
                context, code, Intent(context, WorkoutService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun buildNotification(context: Context, title: String?, paused: Boolean = false): Notification {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title ?: context.getString(R.string.workout_notification_title))
                .setContentText(context.getString(if (paused) R.string.workout_notification_paused else R.string.workout_notification_text))
                .addAction(0, context.getString(if (paused) R.string.workout_action_resume else R.string.workout_action_pause), action(context, 1, ACTION_TOGGLE))
                .addAction(0, context.getString(R.string.workout_action_next), action(context, 3, ACTION_NEXT))
                .addAction(0, context.getString(R.string.workout_action_finish), action(context, 2, ACTION_FINISH))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setContentIntent(open)
                .build()
        }

        fun start(context: Context, title: String?) {
            val i = Intent(context, WorkoutService::class.java).putExtra(EXTRA_TITLE, title)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
