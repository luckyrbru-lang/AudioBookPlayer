package com.example.audiobookplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val positionMs: Long,
    val note: String? = null,
    val dateCreated: Long = System.currentTimeMillis()
)
