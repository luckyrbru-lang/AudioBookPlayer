package com.example.audiobookplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.audiobookplayer.data.model.Chapter

@Dao
interface ChapterDao {
    @Insert
    suspend fun insertChapters(chapters: List<Chapter>)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    suspend fun getChaptersForBook(bookId: Long): List<Chapter>
}
