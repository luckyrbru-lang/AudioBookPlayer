package com.example.audiobookplayer.ui.library

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobookplayer.AudioBookApp
import com.example.audiobookplayer.R
import com.example.audiobookplayer.data.model.Book
import com.example.audiobookplayer.data.scanner.LibraryScanner
import com.example.audiobookplayer.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * Экран библиотеки. Минимальная реализация — список книг + добавление файлов/папки.
 * UI намеренно простой; полноценный адаптер с обложками/прогрессом — следующий шаг.
 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BooksAdapter
    private lateinit var scanner: LibraryScanner
    private val repository by lazy { (application as AudioBookApp).repository }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) addBookFromFiles(uris)
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { addBookFromFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        scanner = LibraryScanner(this)

        recyclerView = findViewById(R.id.recyclerBooks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BooksAdapter { book -> openPlayer(book) }
        recyclerView.adapter = adapter

        findViewById<android.view.View>(R.id.fabAdd).setOnClickListener { showAddMenu(it) }

        lifecycleScope.launch {
            repository.observeAllBooks().collect { books -> adapter.submitList(books) }
        }
    }

    private fun showAddMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("Выбрать MP3-файлы")
            menu.add("Выбрать папку с книгой")
            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Выбрать MP3-файлы" -> pickFiles.launch("audio/*")
                    "Выбрать папку с книгой" -> pickFolder.launch(null)
                }
                true
            }
            show()
        }
    }

    private fun addBookFromFiles(uris: List<Uri>) {
        lifecycleScope.launch {
            val result = scanner.buildBookFromFiles(uris, bookTitle = "Новая книга", author = "Неизвестный автор")
            repository.addBookWithChapters(result.book, result.chapters)
        }
    }

    private fun addBookFromFolder(uri: Uri) {
        lifecycleScope.launch {
            val result = scanner.buildBookFromFolder(uri, fallbackTitle = "Новая книга") ?: return@launch
            repository.addBookWithChapters(result.book, result.chapters)
        }
    }

    private fun openPlayer(book: Book) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_BOOK_ID, book.id))
    }
}
