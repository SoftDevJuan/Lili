package com.example.lili

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    private val binder = TimerBinder()
    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _timerState = MutableStateFlow(TimerData())
    val timerState = _timerState.asStateFlow()

    data class TimerData(
        val timeLeftInMillis: Long = 0,
        val durationInMillis: Long = 0,
        val isRunning: Boolean = false
    )

    companion object {
        const val PROGRESS_CHANNEL_ID = "TIMER_PROGRESS_CHANNEL"
        const val FINISHED_CHANNEL_ID = "TIMER_FINISHED_CHANNEL"
        const val PROGRESS_NOTIFICATION_ID = 1
        const val FINISHED_NOTIFICATION_ID = 2
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val ACTION_TOGGLE_PAUSE_PLAY = "ACTION_TOGGLE_PAUSE_PLAY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent.getLongExtra(EXTRA_DURATION, 0))
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> resetTimer(intent.getLongExtra(EXTRA_DURATION, 0))
            ACTION_TOGGLE_PAUSE_PLAY -> togglePausePlay()
        }
        return START_NOT_STICKY
    }

    private fun togglePausePlay() {
        if (_timerState.value.isRunning) {
            pauseTimer()
        } else {
            startTimer(_timerState.value.timeLeftInMillis)
        }
    }

    fun startTimer(initialDuration: Long) {
        if (_timerState.value.isRunning || initialDuration <= 0) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lili::TimerWakeLock")
        wakeLock?.acquire(initialDuration + 2000L)

        val durationToUse = if (_timerState.value.timeLeftInMillis > 0) _timerState.value.timeLeftInMillis else initialDuration

        startForeground(PROGRESS_NOTIFICATION_ID, buildProgressNotification(durationToUse, true))

        countDownTimer = object : CountDownTimer(durationToUse, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = TimerData(millisUntilFinished, durationToUse, true)
                updateProgressNotification()
            }
            override fun onFinish() {
                releaseWakeLockAndStop(true)
                showFinishedNotification()
                _timerState.value = TimerData(0, durationToUse, false)
                stopSelf()
            }
        }.start()

        _timerState.value = TimerData(durationToUse, durationToUse, true)
    }

    fun pauseTimer() {
        countDownTimer?.cancel()
        _timerState.value = _timerState.value.copy(isRunning = false)
        releaseWakeLockAndStop(false)
        updateProgressNotification()
    }

    fun resetTimer(newDuration: Long) {
        countDownTimer?.cancel()
        _timerState.value = TimerData(newDuration, newDuration, false)
        releaseWakeLockAndStop(true)
    }

    private fun releaseWakeLockAndStop(removeNotification: Boolean) {
        wakeLock?.let {
            if (it.isHeld) { it.release() }
        }
        stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        releaseWakeLockAndStop(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun updateProgressNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(_timerState.value.timeLeftInMillis, _timerState.value.isRunning))
    }

    private fun buildProgressNotification(timeLeft: Long, isRunning: Boolean): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) - TimeUnit.MINUTES.toSeconds(minutes)
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        val toggleIntent = Intent(this, TimerService::class.java).setAction(ACTION_TOGGLE_PAUSE_PLAY)
        val togglePendingIntent = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val actionIcon = if (isRunning) R.drawable.ic_media_pause else R.drawable.ic_media_play
        val actionTitle = if (isRunning) "Pausar" else "Continuar"

        return NotificationCompat.Builder(this, PROGRESS_CHANNEL_ID)
            .setContentTitle("Pomodoro de Lili")
            .setContentText("Tiempo restante: $timeFormatted")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .addAction(actionIcon, actionTitle, togglePendingIntent)
            .build()
    }

    private fun showFinishedNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val soundUri = "android.resource://$packageName/${R.raw.bubbles}".toUri()

        val finishedNotification = NotificationCompat.Builder(this, FINISHED_CHANNEL_ID)
            .setContentTitle("¡Tiempo Terminado!")
            .setContentText("¡Buen trabajo, Guapisima! Es hora de un descanso :)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FINISHED_NOTIFICATION_ID, finishedNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Progreso del Temporizador",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(progressChannel)

            val soundUri = "android.resource://$packageName/${R.raw.bubbles}".toUri()
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val finishedChannel = NotificationChannel(
                FINISHED_CHANNEL_ID,
                "Temporizador Finalizado",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación que suena al terminar un ciclo."
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            nm.createNotificationChannel(finishedChannel)
        }
    }
}