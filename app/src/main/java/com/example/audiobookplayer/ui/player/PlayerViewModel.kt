package com.example.audiobookplayer.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.audiobookplayer.data.model.Bookmark
import com.example.audiobookplayer.data.repository.BookRepository
import com.example.audiobookplayer.player.PlaybackService
import com.example.audiobookplayer.player.SleepTimer
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    context: Context,
    private val repository: BookRepository
) : ViewModel() {

    private var controller: MediaController? = null
    // Позволяет дожидаться готовности controller вместо гонки с ?. — раньше setMediaItems()
    // в loadBook() мог выполниться до того, как MediaController успевал подключиться,
    // и молча ничего не делал (это и было причиной "звук не играет, кнопки не реагируют").
    private val controllerReady = CompletableDeferred<MediaController>()
    private var bookId: Long = -1
    private var chapters = listOf<com.example.audiobookplayer.data.model.Chapter>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _book = MutableStateFlow<com.example.audiobookplayer.data.model.Book?>(null)
    val book: StateFlow<com.example.audiobookplayer.data.model.Book?> = _book.asStateFlow()

    private val sleepTimer = SleepTimer(
        onTick = { secondsLeft -> applyFadeVolume(secondsLeft) },
        onFinish = { withController { it.pause(); it.volume = 1f } } // сбрасываем громкость для следующего раза
    )
    val sleepSecondsLeft: StateFlow<Int> get() = sleepTimer.remainingSeconds

    /** Последние FADE_SECONDS секунд таймера сна звук плавно затихает, а не обрывается резко. */
    private fun applyFadeVolume(secondsLeft: Int) {
        if (secondsLeft in 1..FADE_SECONDS) {
            val volume = secondsLeft / FADE_SECONDS.toFloat()
            withController { it.volume = volume }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(playerListener)
            controllerReady.complete(c)
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentChapterIndex.value = controller?.currentMediaItemIndex ?: 0
        }
    }

    fun loadBook(id: Long) {
        bookId = id
        viewModelScope.launch {
            val book = repository.getBookById(id) ?: return@launch
            _book.value = book
            chapters = repository.getChapters(id)
            _durationMs.value = book.totalDurationMs

            val mediaItems = chapters.map { MediaItem.fromUri(it.fileUri) }
            val c = controllerReady.await() // ждём подключения, а не гадаем ?.
            c.setMediaItems(mediaItems, book.lastChapterIndex, book.lastPositionMs)
            c.prepare()
            c.play()
        }
        viewModelScope.launch {
            repository.observeBookmarks(id).collect { _bookmarks.value = it }
        }
    }

    /** Гарантирует, что действие выполнится ТОЛЬКО когда MediaController реально подключён —
     *  без этого play/pause/seek/etc могли молча ничего не делать, если пользователь нажимал
     *  кнопку быстро после открытия экрана (до завершения асинхронного подключения к сервису). */
    private fun withController(action: (MediaController) -> Unit) {
        viewModelScope.launch { action(controllerReady.await()) }
    }

    fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    /** В отличие от seekTo(), умеет прыгать на другую главу — нужен для перехода по закладке,
     *  которая может указывать не на текущий, а на любой другой файл книги. */
    fun seekToBookmark(bookmark: Bookmark) = withController {
        it.seekTo(bookmark.chapterIndex, bookmark.positionMs)
        _currentChapterIndex.value = bookmark.chapterIndex
        _positionMs.value = bookmark.positionMs
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun skipForward(ms: Long = 30_000) = withController {
        val target = (it.currentPosition + ms).coerceAtMost(it.duration.coerceAtLeast(0))
        it.seekTo(target)
        _positionMs.value = target
    }

    fun skipBack(ms: Long = 15_000) = withController {
        val target = (it.currentPosition - ms).coerceAtLeast(0)
        it.seekTo(target)
        _positionMs.value = target
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun startSleepTimer(minutes: Int) {
        withController { it.volume = 1f } // на случай, если предыдущее затухание не успело сброситься
        sleepTimer.start(minutes)
    }
    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)
    fun cancelSleepTimer() {
        withController { it.volume = 1f } // отмена посреди затухания не должна оставлять звук тихим
        sleepTimer.stop()
    }

    /** Обновляет прогресс раз в секунду — вызывается из Activity через свой таймер/handler. */
    fun tickProgress() {
        val c = controller ?: return
        _positionMs.value = c.currentPosition
        viewModelScope.launch {
            repository.saveProgress(bookId, c.currentMediaItemIndex, c.currentPosition, _durationMs.value)
        }
    }

    fun addBookmark(note: String? = null) = withController { c ->
        viewModelScope.launch {
            repository.addBookmark(
                Bookmark(bookId = bookId, chapterIndex = c.currentMediaItemIndex, positionMs = c.currentPosition, note = note)
            )
        }
    }

    override fun onCleared() {
        controller?.release()
        sleepTimer.stop()
        super.onCleared()
    }

    companion object {
        private const val FADE_SECONDS = 10
    }
}
