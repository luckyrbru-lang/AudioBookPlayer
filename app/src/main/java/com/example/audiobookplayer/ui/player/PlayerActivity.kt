package com.example.audiobookplayer.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.example.audiobookplayer.AudioBookApp
import com.example.audiobookplayer.R
import com.example.audiobookplayer.utils.ViewModelFactory
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    val viewModelFactory by lazy {
        ViewModelFactory(applicationContext, (application as AudioBookApp).repository)
    }

    private val viewModel: PlayerViewModel by viewModels { viewModelFactory }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            viewModel.tickProgress()
            progressHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        if (bookId == -1L) { finish(); return }
        viewModel.loadBook(bookId)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnBookmarks).setOnClickListener {
            BookmarksBottomSheet().show(supportFragmentManager, BookmarksBottomSheet.TAG)
        }

        findViewById<android.widget.ImageButton>(R.id.btnPlayPause).setOnClickListener {
            viewModel.togglePlayPause()
        }
        findViewById<android.widget.ImageButton>(R.id.btnSkipForward).setOnClickListener { viewModel.skipForward() }
        findViewById<android.widget.ImageButton>(R.id.btnSkipBack).setOnClickListener { viewModel.skipBack() }

        findViewById<android.widget.SeekBar>(R.id.progressSeekBar).setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) viewModel.seekTo(progress.toLong())
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            }
        )

        findViewById<android.widget.TextView>(R.id.btnSpeed).setOnClickListener { showSpeedDialog() }
        findViewById<android.widget.TextView>(R.id.btnSleepTimer).setOnClickListener { showSleepTimerDialog() }
        findViewById<android.widget.TextView>(R.id.btnAddBookmark).setOnClickListener {
            showAddBookmarkDialog()
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isPlaying.collect { playing ->
                        findViewById<android.widget.ImageButton>(R.id.btnPlayPause).setImageResource(
                            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        )
                    }
                }
                launch {
                    viewModel.durationMs.collect { d ->
                        findViewById<android.widget.SeekBar>(R.id.progressSeekBar).max = d.toInt().coerceAtLeast(1)
                        findViewById<android.widget.TextView>(R.id.tvDuration).text = formatTime(d)
                    }
                }
                launch {
                    viewModel.positionMs.collect { p ->
                        findViewById<android.widget.SeekBar>(R.id.progressSeekBar).progress = p.toInt()
                        findViewById<android.widget.TextView>(R.id.tvPosition).text = formatTime(p)
                    }
                }
            }
        }
    }

    private fun showAddBookmarkDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Заметка (необязательно)"
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        AlertDialog.Builder(this)
            .setTitle("♠ Новая закладка")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val note = input.text.toString().trim().ifBlank { null }
                viewModel.addBookmark(note)
                android.widget.Toast.makeText(this, "Закладка добавлена", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val values = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog.Builder(this)
            .setTitle("Скорость воспроизведения")
            .setItems(speeds) { _, which ->
                viewModel.setSpeed(values[which])
                findViewById<android.widget.TextView>(R.id.btnSpeed).text = "♦ ${speeds[which]}"
            }.show()
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("5 минут", "15 минут", "30 минут", "60 минут", "Отключить")
        val minutes = intArrayOf(5, 15, 30, 60, 0)
        AlertDialog.Builder(this)
            .setTitle("Таймер сна")
            .setItems(options) { _, which ->
                if (minutes[which] == 0) viewModel.cancelSleepTimer() else viewModel.startSleepTimer(minutes[which])
            }.show()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = TimeUnit.SECONDS.toHours(totalSec)
        val m = TimeUnit.SECONDS.toMinutes(totalSec) % 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    override fun onStart() {
        super.onStart()
        progressHandler.post(progressRunnable)
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressRunnable)
        super.onStop()
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
