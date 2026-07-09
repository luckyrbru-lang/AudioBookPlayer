package com.example.audiobookplayer.player

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Таймер сна. Последние 10 секунд плавно приглушает звук (fade-out),
 * а не обрывает воспроизведение резко — как сделано в Яндекс Книгах.
 */
class SleepTimer(private val onTick: (secondsLeft: Int) -> Unit, private val onFinish: () -> Unit) {

    private var timer: CountDownTimer? = null

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    val isActive: Boolean get() = timer != null

    fun start(minutes: Int) {
        stop()
        val totalMs = minutes * 60_000L
        timer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(msLeft: Long) {
                val secs = (msLeft / 1000).toInt()
                _remainingSeconds.value = secs
                onTick(secs)
            }

            override fun onFinish() {
                _remainingSeconds.value = 0
                timer = null
                onFinish()
            }
        }.start()
    }

    /** "Ещё N минут" — популярная кнопка в UI Яндекс Книг: продлевает текущий таймер. */
    fun extend(minutes: Int) {
        val currentSecs = _remainingSeconds.value
        start((currentSecs / 60) + minutes)
    }

    fun stop() {
        timer?.cancel()
        timer = null
        _remainingSeconds.value = 0
    }
}
