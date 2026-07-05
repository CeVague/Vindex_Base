package com.cevague.vindex.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.R
import com.cevague.vindex.databinding.FragmentAlbumsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumsViewModel by viewModels()
    private lateinit var adapter: AlbumsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenWidth = requireContext().resources.displayMetrics.widthPixels
        adapter = AlbumsAdapter(screenWidth / ALBUM_COLUMNS) { card ->
            val args = when (val target = card.target) {
                is AlbumListItem.Card.Target.Folder ->
                    bundleOf(AlbumDetailFragment.ARG_FOLDER_PATH to target.folderPath)
                is AlbumListItem.Card.Target.Album ->
                    bundleOf(AlbumDetailFragment.ARG_ALBUM_ID to target.albumId)
            }
            findNavController().navigate(R.id.action_albums_to_detail, args)
        }

        val layoutManager = GridLayoutManager(requireContext(), ALBUM_COLUMNS)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.getItemViewType(position) == AlbumsAdapter.VIEW_TYPE_HEADER) ALBUM_COLUMNS else 1
        }

        binding.recyclerAlbums.apply {
            this.adapter = this@AlbumsFragment.adapter
            this.layoutManager = layoutManager
            setHasFixedSize(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    adapter.submitList(items)
                    binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerAlbums.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ALBUM_COLUMNS = 2
    }
}
