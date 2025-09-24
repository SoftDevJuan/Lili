package com.example.lili

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Este objeto es un Singleton, lo que significa que solo hay una instancia de él en toda la app.
// Actúa como una fuente de verdad única y centralizada.
object TimerState {
    // Un StateFlow es un flujo de datos observable que siempre tiene un valor.
    // Es perfecto para representar el estado de la UI.
    private val _timerData = MutableStateFlow(TimerData())
    val timerData = _timerData.asStateFlow() // Versión de solo lectura para la UI

    fun update(data: TimerData) {
        _timerData.value = data
    }
}

// Una clase de datos simple para mantener toda la información del timer junta.
data class TimerData(
    val timeLeftInMillis: Long = 0,
    val isRunning: Boolean = false,
    val durationInMillis: Long = 0
)