package com.example.lili

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

fun scheduleDailyAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // --- Alarma de las 7 AM ---
    val morningIntent = Intent(context, NotificationReceiver::class.java).apply {
        putExtra(NotificationReceiver.EXTRA_MESSAGE, "¡Buenos días! Negrita de cafe ☀️ Es hora de empezar tu día con alegria y rico cafe, Dios bendiga tu vida hoy y siempre, Guapa!.")
    }
    val morningPendingIntent = PendingIntent.getBroadcast(context, 101, morningIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val morningCalendar: Calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 32)
        set(Calendar.SECOND, 0)
    }
    // Si la hora ya pasó hoy, la programamos para mañana
    if (morningCalendar.timeInMillis <= System.currentTimeMillis()) {
        morningCalendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    alarmManager.setRepeating(
        AlarmManager.RTC_WAKEUP,
        morningCalendar.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        morningPendingIntent
    )

    // --- Alarma de las 9 PM ---
    val eveningIntent = Intent(context, NotificationReceiver::class.java).apply {
        putExtra(NotificationReceiver.EXTRA_MESSAGE, "¡Buenas noches, Guapisima 🌙 Dios te bendiga hoy y siempre, eres maravillosa!")
    }
    val eveningPendingIntent = PendingIntent.getBroadcast(context, 102, eveningIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val eveningCalendar: Calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 14) // 9 PM
        set(Calendar.MINUTE, 31)
        set(Calendar.SECOND, 0)
    }
    if (eveningCalendar.timeInMillis <= System.currentTimeMillis()) {
        eveningCalendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    alarmManager.setRepeating(
        AlarmManager.RTC_WAKEUP,
        eveningCalendar.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        eveningPendingIntent
    )
}