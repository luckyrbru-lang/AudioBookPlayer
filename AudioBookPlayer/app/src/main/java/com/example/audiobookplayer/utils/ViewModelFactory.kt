package com.example.audiobookplayer.utils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audiobookplayer.data.repository.BookRepository
import com.example.audiobookplayer.ui.player.PlayerViewModel

/**
 * Без Hilt: простая ручная фабрика. Для проекта такого размера
 * это быстрее и прозрачнее, DI можно добавить позже при росте кодовой базы.
 */
class ViewModelFactory(
    private val appContext: Context,
    private val repository: BookRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            return PlayerViewModel(appContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
