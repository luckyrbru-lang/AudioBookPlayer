package com.example.audiobookplayer.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.audiobookplayer.data.model.Book
import com.example.audiobookplayer.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Читает метаданные MP3-файлов без сторонних библиотек — MediaMetadataRetriever
 * входит в стандартный Android SDK и корректно читает ID3-теги.
 *
 * Поддерживает два сценария:
 *  1) pickSingleFiles — пользователь вручную выбрал один или несколько MP3 (SAF, GetMultipleContents)
 *  2) scanFolder       — пользователь выбрал папку (SAF, OpenDocumentTree), все MP3 внутри
 *                        группируются в одну книгу, отсортированную по имени файла (главы)
 */
class LibraryScanner(private val context: Context) {

    data class ScanResult(val book: Book, val chapters: List<Chapter>)

    /** Вариант 1: несколько выбранных вручную файлов = одна книга (главы = файлы). */
    suspend fun buildBookFromFiles(uris: List<Uri>, bookTitle: String, author: String): ScanResult =
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                // ACTION_GET_CONTENT (через который приходят эти uri) не всегда выдаёт
                // "вечное" (persistable) разрешение — в отличие от выбора папки.
                // Без try/catch SecurityException тут падало всё приложение целиком.
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Не страшно: файл всё равно прочитается сейчас и будет играть в этой сессии.
                    // После перезапуска приложения доступ может пропасть — тогда нужно
                    // будет выбрать файл заново (это ограничение ACTION_GET_CONTENT, не баг).
                }
            }
            val chapters = uris.mapIndexed { index, uri -> readChapter(uri, index) }
            val totalDuration = chapters.sumOf { it.durationMs }
            val coverPath = uris.firstOrNull()?.let { extractCover(it) }
            ScanResult(
                book = Book(title = bookTitle, author = author, totalDurationMs = totalDuration, coverPath = coverPath),
                chapters = chapters
            )
        }

    /** Вариант 2: сканирование выбранной папки — все mp3 внутри становятся главами одной книги. */
    suspend fun buildBookFromFolder(folderUri: Uri, fallbackTitle: String): ScanResult? =
        withContext(Dispatchers.IO) {
            context.contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            val audioFiles = folder.listFiles()
                .filter { it.isFile && (it.type?.startsWith("audio/") == true) }
                .sortedBy { it.name }

            if (audioFiles.isEmpty()) return@withContext null

            val chapters = audioFiles.mapIndexed { index, file -> readChapter(file.uri, index, file.name) }
            val totalDuration = chapters.sumOf { it.durationMs }
            // Автор/название пытаемся взять из тегов первого файла, иначе — имя папки
            val retriever = MediaMetadataRetriever()
            var title = fallbackTitle
            var author = "Неизвестный автор"
            try {
                retriever.setDataSource(context, audioFiles.first().uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { author = it }
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }
            val coverPath = extractCover(audioFiles.first().uri)

            ScanResult(
                book = Book(
                    title = title, author = author,
                    totalDurationMs = totalDuration,
                    sourceFolderUri = folderUri.toString(),
                    coverPath = coverPath
                ),
                chapters = chapters
            )
        }

    /** Достаёт обложку из ID3-тегов (METADATA_KEY, встроенная картинка альбома) и
     *  сохраняет её в приватное хранилище приложения. Возвращает null, если у файла
     *  нет встроенной обложки — тогда в UI покажется наш собственный плейсхолдер. */
    private fun extractCover(uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val coversDir = java.io.File(context.filesDir, "covers").apply { mkdirs() }
            val file = java.io.File(coversDir, "${java.util.UUID.randomUUID()}.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun readChapter(uri: Uri, index: Int, fallbackName: String? = null): Chapter {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var title = fallbackName ?: "Глава ${index + 1}"
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
        } catch (_: Exception) {
            // Повреждённый/нечитаемый файл — пропускаем метаданные, оставляем длительность 0
        } finally {
            retriever.release()
        }
        return Chapter(
            bookId = 0, // проставится в BookRepository.addBookWithChapters
            title = title,
            fileUri = uri.toString(),
            durationMs = durationMs,
            orderIndex = index
        )
    }
}
