package com.example.audiobookplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Одна аудиокнига. Сами MP3-файлы (главы) хранятся в таблице "chapters"
 * и связаны через bookId — так книга может состоять из одного файла или из десятков.
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val coverPath: String? = null,
    val totalDurationMs: Long = 0,
    val lastChapterIndex: Int = 0,     // на какой главе (файле) остановились
    val lastPositionMs: Long = 0,      // позиция внутри этой главы
    val progressPercent: Float = 0f,
    val dateAdded: Long = System.currentTimeMillis(),
    val sourceFolderUri: String? = null // если книга добавлена сканированием папки — для повторного скана
)
