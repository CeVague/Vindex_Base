package com.cevague.vindex.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.FragmentAlbumDetailBinding
import com.cevague.vindex.ui.search.SearchResultAdapter
import com.cevague.vindex.ui.viewer.PhotoViewerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumDetailViewModel by viewModels()

    @Inject
    lateinit var settingsCache: SettingsCache

    private lateinit var adapter: SearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val columns = settingsCache.gridColumns
        val screenWidth = requireContext().resources.displayMetrics.widthPixels

        adapter = SearchResultAdapter(screenWidth / columns) { photo ->
            PhotoViewerActivity.start(requireContext(), viewModel.viewerSource(photo.id))
        }

        binding.recyclerDetail.apply {
            this.adapter = this@AlbumDetailFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), columns)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collect { photos ->
                        adapter.submitPhotos(photos)
                        binding.textEmpty.visibility =
                            if (photos.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.title.collect { title ->
                        if (title.isNotEmpty()) {
                            (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
                                title
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_FOLDER_PATH = "folderPath"
        const val ARG_ALBUM_ID = "albumId"
    }
}
