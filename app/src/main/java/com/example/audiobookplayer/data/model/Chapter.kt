package com.example.audiobookplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один MP3-файл (глава) внутри книги. orderIndex задаёт порядок воспроизведения.
 */
@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val title: String,          // имя файла или тег title, если есть
    val fileUri: String,        // content:// или file:// путь
    val durationMs: Long,
    val orderIndex: Int
)
