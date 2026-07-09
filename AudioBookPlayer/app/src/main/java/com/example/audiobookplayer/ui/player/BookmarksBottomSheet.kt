package com.example.audiobookplayer.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobookplayer.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Список закладок текущей книги. Открывается поверх PlayerActivity, использует тот же
 * PlayerViewModel (через activityViewModels) — то есть не нужно ничего заново грузить.
 * Тап по закладке — переход к позиции (с переключением главы, если нужно) и закрытие листа.
 */
class BookmarksBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: PlayerViewModel by activityViewModels {
        (requireActivity() as PlayerActivity).viewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_bookmarks, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerBookmarks)
        val emptyView = view.findViewById<View>(R.id.tvEmptyBookmarks)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = BookmarksAdapter(
            onClick = { bookmark ->
                viewModel.seekToBookmark(bookmark)
                dismiss()
            },
            onDelete = { bookmark -> viewModel.deleteBookmark(bookmark) }
        )
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bookmarks.collect { list ->
                    adapter.submitList(list)
                    emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    companion object {
        const val TAG = "BookmarksBottomSheet"
    }
}
