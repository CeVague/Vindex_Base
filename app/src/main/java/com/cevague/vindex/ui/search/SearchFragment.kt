package com.cevague.vindex.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.FragmentSearchBinding
import com.cevague.vindex.ui.gallery.GalleryAdapter
import com.cevague.vindex.ui.gallery.GalleryItem
import com.cevague.vindex.ui.gallery.PhotoGrouper
import com.cevague.vindex.ui.main.MainSharedViewModel
import com.cevague.vindex.ui.viewer.PhotoViewerActivity
import com.cevague.vindex.ui.viewer.ViewerSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    @Inject
    lateinit var settingsCache: SettingsCache

    @Inject
    lateinit var photoGrouper: PhotoGrouper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = GalleryAdapter(getTargetSize(requireContext())) { photoSummary, position ->
            val photosOnly = (binding.recyclerSearch.adapter as GalleryAdapter).getPhotosOnly()

            if (photosOnly.isNotEmpty()) {
                val photoIds = photosOnly.map { it.id }

                val source = ViewerSource.Search(
                    photoIds = photoIds,
                    startPhotoId = photoSummary.id
                )

                PhotoViewerActivity.start(requireContext(), source)
            }
        }

        val spanCount = settingsCache.gridColumns

        val gridLayoutManager = GridLayoutManager(requireContext(), spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        GalleryAdapter.VIEW_TYPE_HEADER -> spanCount
                        else -> 1
                    }
                }
            }

            spanSizeLookup.isSpanIndexCacheEnabled = true
            spanSizeLookup.isSpanGroupIndexCacheEnabled = true
        }

        binding.recyclerSearch.apply {
            this.adapter = adapter
            this.layoutManager = gridLayoutManager

            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        // 3. Écouter la saisie utilisateur
        binding.inputSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.performSearch(query ?: "")
                binding.inputSearch.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Tu peux décommenter pour une recherche "live"
                // viewModel.performSearch(newText ?: "")
                return true
            }
        })

        // 4. Observation réactive des résultats
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResults.collect { photos ->
                    val items = photos.map { GalleryItem.PhotoItem(it) }
                    adapter.submitData(PagingData.from(items))
                    updateUIState(photos)
                }
            }
        }

        // 5. Gestion de la recherche partagée (People -> Search)
        val sharedViewModel: MainSharedViewModel by activityViewModels()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.searchQuery.collect { query ->
                    if (!query.isNullOrEmpty()) {
                        binding.inputSearch.post {
                            binding.inputSearch.setQuery(query, true)
                            sharedViewModel.clearSearchQuery()
                        }
                    }
                }
            }
        }
    }

    fun getTargetSize(context: Context): Int {
        val spanCount = settingsCache.gridColumns
        val screenWidth = context.resources.displayMetrics.widthPixels
        return screenWidth / (spanCount * 2)
    }

    private fun updateUIState(photos: List<PhotoSummary>) {
        val query = binding.inputSearch.query.toString()
        if (query.length < 2) {
            binding.textEmpty.visibility = View.GONE
        } else {
            binding.textEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
