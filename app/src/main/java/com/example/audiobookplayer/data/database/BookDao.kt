package com.example.audiobookplayer.data.database

import androidx.room.*
import com.example.audiobookplayer.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    // Flow => экран библиотеки обновляется автоматически при любом изменении в БД
    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun observeAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): Book?

    @Query("""
        UPDATE books
        SET lastChapterIndex = :chapterIndex, lastPositionMs = :positionMs, progressPercent = :percent
        WHERE id = :bookId
    """)
    suspend fun updateProgress(bookId: Long, chapterIndex: Int, positionMs: Long, percent: Float)
}
