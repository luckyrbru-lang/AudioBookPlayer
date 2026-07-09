package com.example.audiobookplayer.data.repository

import com.example.audiobookplayer.data.database.AppDatabase
import com.example.audiobookplayer.data.model.Book
import com.example.audiobookplayer.data.model.Bookmark
import com.example.audiobookplayer.data.model.Chapter
import kotlinx.coroutines.flow.Flow

class BookRepository(private val db: AppDatabase) {

    fun observeAllBooks(): Flow<List<Book>> = db.bookDao().observeAllBooks()

    suspend fun getBookById(bookId: Long): Book? = db.bookDao().getBookById(bookId)

    suspend fun getChapters(bookId: Long): List<Chapter> = db.chapterDao().getChaptersForBook(bookId)

    /** Добавляет книгу вместе с её главами (файлами) в одной операции. */
    suspend fun addBookWithChapters(book: Book, chapters: List<Chapter>): Long {
        val bookId = db.bookDao().insertBook(book)
        db.chapterDao().insertChapters(chapters.map { it.copy(bookId = bookId) })
        return bookId
    }

    suspend fun saveProgress(bookId: Long, chapterIndex: Int, positionMs: Long, totalDurationMs: Long) {
        val percent = if (totalDurationMs > 0) positionMs.toFloat() / totalDurationMs * 100f else 0f
        db.bookDao().updateProgress(bookId, chapterIndex, positionMs, percent)
    }

    suspend fun deleteBook(book: Book) = db.bookDao().deleteBook(book)

    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = db.bookmarkDao().observeBookmarksForBook(bookId)

    suspend fun addBookmark(bookmark: Bookmark) = db.bookmarkDao().insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = db.bookmarkDao().deleteBookmark(bookmark)
}
