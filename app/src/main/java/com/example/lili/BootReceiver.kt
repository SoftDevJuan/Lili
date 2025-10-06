package com.example.lili

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            // Cuando el teléfono termina de encenderse, volvemos a programar las alarmas
            scheduleDailyAlarms(context)
        }
    }
}