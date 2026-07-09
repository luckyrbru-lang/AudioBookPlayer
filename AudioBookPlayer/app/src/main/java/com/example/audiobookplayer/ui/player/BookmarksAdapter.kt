package com.example.audiobookplayer.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobookplayer.R
import com.example.audiobookplayer.data.model.Bookmark
import java.util.concurrent.TimeUnit

class BookmarksAdapter(
    private val onClick: (Bookmark) -> Unit,
    private val onDelete: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.VH>() {

    private var items: List<Bookmark> = emptyList()

    fun submitList(bookmarks: List<Bookmark>) {
        items = bookmarks
        notifyDataSetChanged()
    }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.tvBookmarkTime)
        val note: TextView = view.findViewById(R.id.tvBookmarkNote)
        val delete: ImageButton = view.findViewById(R.id.btnDeleteBookmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val bookmark = items[position]
        holder.time.text = formatTime(bookmark.positionMs)
        if (bookmark.note.isNullOrBlank()) {
            holder.note.text = "Глава ${bookmark.chapterIndex + 1}"
        } else {
            holder.note.text = bookmark.note
        }
        holder.itemView.setOnClickListener { onClick(bookmark) }
        holder.delete.setOnClickListener { onDelete(bookmark) }
    }

    override fun getItemCount() = items.size

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = TimeUnit.SECONDS.toHours(totalSec)
        val m = TimeUnit.SECONDS.toMinutes(totalSec) % 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}
