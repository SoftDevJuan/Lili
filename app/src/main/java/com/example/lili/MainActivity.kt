package com.example.lili

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.lili.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private enum class TimerMode { FOCUS, SHORT_BREAK, LONG_BREAK }
    private lateinit var binding: ActivityMainBinding
    private var currentMode = TimerMode.FOCUS

    private val ONE_MINUTE_IN_MILLIS = TimeUnit.MINUTES.toMillis(1)

    private var timerService: TimerService? = null
    private var isBound = false
    private lateinit var sharedPreferences: SharedPreferences

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            isBound = true

            val initialDuration = getDurationFromInput(currentMode)
            if (timerService?.timerState?.value?.isRunning == false) {
                timerService?.resetTimer(initialDuration)
            }

            observeTimerState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            timerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)

        loadDurations()
        setupClickListeners()

        val initialDuration = getDurationFromInput(currentMode)
        updateTimerUI(initialDuration, initialDuration)
        updateControlButtonsUI(false)
        updateModeUI()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (timerService?.timerState?.value?.isRunning == false) {
            stopService(Intent(this, TimerService::class.java))
        }
    }

    private fun observeTimerState() {
        timerService?.timerState
            ?.onEach { timerData ->
                updateTimerUI(timerData.timeLeftInMillis, timerData.durationInMillis)
                updateControlButtonsUI(timerData.isRunning)
            }
            ?.launchIn(lifecycleScope)
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener {
            val duration = getDurationFromInput(currentMode)
            val serviceIntent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_DURATION, duration)
            }
            startService(serviceIntent)
        }
        binding.btnPause.setOnClickListener {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
        }
        binding.btnReset.setOnClickListener { selectMode(currentMode, true) }

        binding.btnFocus.setOnClickListener { selectMode(TimerMode.FOCUS) }
        binding.btnShortBreak.setOnClickListener { selectMode(TimerMode.SHORT_BREAK) }
        binding.btnLongBreak.setOnClickListener { selectMode(TimerMode.LONG_BREAK) }

        binding.timeText.setOnClickListener {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_TOGGLE_PAUSE_PLAY))
        }

        binding.leftClickArea.setOnClickListener { adjustTime(-ONE_MINUTE_IN_MILLIS) }
        binding.rightClickArea.setOnClickListener { adjustTime(ONE_MINUTE_IN_MILLIS) }

        addTextWatcherToInput(binding.inputFocusMins, TimerMode.FOCUS, "focus_duration")
        addTextWatcherToInput(binding.inputShortMins, TimerMode.SHORT_BREAK, "short_break_duration")
        addTextWatcherToInput(binding.inputLongMins, TimerMode.LONG_BREAK, "long_break_duration")
    }

    private fun addTextWatcherToInput(editText: EditText, mode: TimerMode, prefKey: String) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val valueToSave = s.toString().toLongOrNull() ?: getDefaultMinutes(mode)
                saveDuration(prefKey, valueToSave)

                if (currentMode == mode && timerService?.timerState?.value?.isRunning == false) {
                    val newDuration = getDurationFromInput(mode)
                    val serviceIntent = Intent(this@MainActivity, TimerService::class.java).apply {
                        action = TimerService.ACTION_RESET
                        putExtra(TimerService.EXTRA_DURATION, newDuration)
                    }
                    startService(serviceIntent)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun selectMode(mode: TimerMode, forceReset: Boolean = false) {
        if (currentMode != mode || forceReset) {
            currentMode = mode
            val newDuration = getDurationFromInput(mode)
            val serviceIntent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_RESET
                putExtra(TimerService.EXTRA_DURATION, newDuration)
            }
            startService(serviceIntent)
            updateModeUI()
        }
    }

    private fun saveDuration(key: String, value: Long) {
        with(sharedPreferences.edit()) {
            putLong(key, value)
            apply()
        }
    }

    private fun loadDurations() {
        val focusMins = sharedPreferences.getLong("focus_duration", 25)
        val shortBreakMins = sharedPreferences.getLong("short_break_duration", 5)
        val longBreakMins = sharedPreferences.getLong("long_break_duration", 15)

        binding.inputFocusMins.setText(focusMins.toString())
        binding.inputShortMins.setText(shortBreakMins.toString())
        binding.inputLongMins.setText(longBreakMins.toString())
    }

    private fun getDurationFromInput(mode: TimerMode): Long {
        val input = when (mode) {
            TimerMode.FOCUS -> binding.inputFocusMins.text.toString()
            TimerMode.SHORT_BREAK -> binding.inputShortMins.text.toString()
            TimerMode.LONG_BREAK -> binding.inputLongMins.text.toString()
        }
        val defaultMinutes = getDefaultMinutes(mode)
        val minutes = if (input.isBlank()) defaultMinutes else input.toLongOrNull() ?: defaultMinutes
        return TimeUnit.MINUTES.toMillis(minutes)
    }

    private fun getDefaultMinutes(mode: TimerMode): Long {
        return when (mode) {
            TimerMode.FOCUS -> 25L
            TimerMode.SHORT_BREAK -> 5L
            TimerMode.LONG_BREAK -> 15L
        }
    }

    private fun adjustTime(millisToAdd: Long) {
        val currentDuration = timerService?.timerState?.value?.durationInMillis ?: getDurationFromInput(currentMode)
        if (timerService?.timerState?.value?.isRunning == true) return

        val newDuration = max(ONE_MINUTE_IN_MILLIS, min(currentDuration + millisToAdd, TimeUnit.MINUTES.toMillis(90)))
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET
            putExtra(TimerService.EXTRA_DURATION, newDuration)
        }
        startService(serviceIntent)
    }

    private fun updateTimerUI(timeLeft: Long, totalDuration: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) - TimeUnit.MINUTES.toSeconds(minutes)
        binding.timeText.text = String.format("%02d:%02d", minutes, seconds)

        val progress = if (totalDuration > 0) (timeLeft.toDouble() / totalDuration * 100).toInt() else 100
        binding.progressBar.progress = progress
    }

    private fun updateControlButtonsUI(isRunning: Boolean) {
        binding.btnStart.visibility = if (isRunning) View.GONE else View.VISIBLE
        binding.btnPause.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    private fun loadGif(resourceId: Int) {
        Glide.with(this)
            .asGif()
            .load(resourceId)
            .circleCrop() // <-- **LÍNEA CLAVE AÑADIDA**
            .into(binding.gifImageView)
    }

    private fun updateModeUI() {
        when (currentMode) {
            TimerMode.FOCUS -> {
                binding.labelText.text = "Enfoque"
                binding.btnFocus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.good)
                binding.btnShortBreak.backgroundTintList = null
                binding.btnLongBreak.backgroundTintList = null
                loadGif(R.raw.focus_mode)
            }
            TimerMode.SHORT_BREAK -> {
                binding.labelText.text = "Pausa Corta"
                binding.btnFocus.backgroundTintList = null
                binding.btnShortBreak.backgroundTintList = ContextCompat.getColorStateList(this, R.color.good)
                binding.btnLongBreak.backgroundTintList = null
                loadGif(R.raw.short_mode)
            }
            TimerMode.LONG_BREAK -> {
                binding.labelText.text = "Pausa Larga"
                binding.btnFocus.backgroundTintList = null
                binding.btnShortBreak.backgroundTintList = null
                binding.btnLongBreak.backgroundTintList = ContextCompat.getColorStateList(this, R.color.good)
                loadGif(R.raw.long_break)
            }
        }
    }
}