package com.example.audiobookplayer

import android.app.Application
import com.example.audiobookplayer.data.database.AppDatabase
import com.example.audiobookplayer.data.repository.BookRepository

class AudioBookApp : Application() {
    lateinit var repository: BookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BookRepository(AppDatabase.getInstance(this))
    }
}
