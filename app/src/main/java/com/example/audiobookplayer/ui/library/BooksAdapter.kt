package com.example.audiobookplayer.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.audiobookplayer.R
import com.example.audiobookplayer.data.model.Book

class BooksAdapter(
    private val onClick: (Book) -> Unit,
    private val onDelete: (Book) -> Unit
) : RecyclerView.Adapter<BooksAdapter.VH>() {

    private var items: List<Book> = emptyList()

    fun submitList(books: List<Book>) {
        items = books
        notifyDataSetChanged()
    }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.ivBookCover)
        val title: TextView = view.findViewById(R.id.tvBookTitle)
        val author: TextView = view.findViewById(R.id.tvBookAuthor)
        val progress: TextView = view.findViewById(R.id.tvBookProgress)
        val delete: ImageButton = view.findViewById(R.id.btnDeleteBook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val book = items[position]
        holder.title.text = book.title
        holder.author.text = book.author
        holder.progress.text = "${book.progressPercent.toInt()}%"
        Glide.with(holder.itemView)
            .load(book.coverPath)
            .placeholder(R.drawable.ic_cover_placeholder)
            .error(R.drawable.ic_cover_placeholder)
            .centerCrop()
            .into(holder.cover)
        holder.itemView.setOnClickListener { onClick(book) }
        holder.delete.setOnClickListener { onDelete(book) }
    }

    override fun getItemCount() = items.size
}
