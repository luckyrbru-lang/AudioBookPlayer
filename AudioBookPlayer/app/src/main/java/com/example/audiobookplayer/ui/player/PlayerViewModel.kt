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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    context: Context,
    private val repository: BookRepository
) : ViewModel() {

    private var controller: MediaController? = null
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

    private val sleepTimer = SleepTimer(
        onTick = { },
        onFinish = { controller?.pause() }
    )
    val sleepSecondsLeft: StateFlow<Int> get() = sleepTimer.remainingSeconds

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
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
            chapters = repository.getChapters(id)
            _durationMs.value = book.totalDurationMs

            val mediaItems = chapters.map { MediaItem.fromUri(it.fileUri) }
            controller?.setMediaItems(mediaItems, book.lastChapterIndex, book.lastPositionMs)
            controller?.prepare()
            controller?.play()

        }
        viewModelScope.launch {
            repository.observeBookmarks(id).collect { _bookmarks.value = it }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    /** В отличие от seekTo(), умеет прыгать на другую главу — нужен для перехода по закладке,
     *  которая может указывать не на текущий, а на любой другой файл книги. */
    fun seekToBookmark(bookmark: Bookmark) {
        controller?.seekTo(bookmark.chapterIndex, bookmark.positionMs)
        _currentChapterIndex.value = bookmark.chapterIndex
        _positionMs.value = bookmark.positionMs
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun skipForward(ms: Long = 30_000) = controller?.let { seekTo((it.currentPosition + ms).coerceAtMost(it.duration)) }
    fun skipBack(ms: Long = 15_000) = controller?.let { seekTo((it.currentPosition - ms).coerceAtLeast(0)) }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun startSleepTimer(minutes: Int) = sleepTimer.start(minutes)
    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)
    fun cancelSleepTimer() = sleepTimer.stop()

    /** Обновляет прогресс раз в секунду — вызывается из Activity через свой таймер/handler. */
    fun tickProgress() {
        val c = controller ?: return
        _positionMs.value = c.currentPosition
        viewModelScope.launch {
            repository.saveProgress(bookId, c.currentMediaItemIndex, c.currentPosition, _durationMs.value)
        }
    }

    fun addBookmark(note: String? = null) {
        val c = controller ?: return
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
}
