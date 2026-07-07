package com.cevague.vindex.ui.search

import android.annotation.SuppressLint
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
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.FragmentSearchBinding
import com.cevague.vindex.search.SearchSessionRepository
import com.cevague.vindex.ui.main.MainSharedViewModel
import com.cevague.vindex.ui.viewer.PhotoViewerActivity
import com.google.android.material.chip.Chip
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
    lateinit var searchSessionRepository: SearchSessionRepository

    private lateinit var adapter: SearchResultAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private var pendingScrollToTop = false

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

        adapter = SearchResultAdapter(getTargetSize(settingsCache.gridColumns)) { photo ->
            val ids = adapter.photos.map { it.id }
            if (ids.isNotEmpty()) {
                val sessionId = searchSessionRepository.put(ids)
                PhotoViewerActivity.start(
                    requireContext(),
                    ViewerSource.Search(sessionId = sessionId, startPhotoId = photo.id)
                )
            }
        }

        gridLayoutManager = GridLayoutManager(requireContext(), settingsCache.gridColumns)

        binding.recyclerSearch.apply {
            this.adapter = this@SearchFragment.adapter
            this.layoutManager = gridLayoutManager
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            itemAnimator = null // Désactive les animations pour éviter que le scroll ne "suive" une vignette
        }

        observeGridColumns()

        binding.inputSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.performSearch(query ?: "")
                binding.inputSearch.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean = true
        })

        // Pre-load on focus
        val searchEditText = binding.inputSearch.findViewById<View>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) viewModel.onSearchFocused()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitPhotos(state.results, state.scores, settingsCache.showScores) {
                        if (pendingScrollToTop) {
                            binding.recyclerSearch.post {
                                gridLayoutManager.scrollToPositionWithOffset(0, 0)
                            }
                            pendingScrollToTop = false
                        }
                    }
                    renderFilterChips(state)
                    binding.textEmpty.visibility =
                        if (state.hasSearched && !state.isLoading && state.results.isEmpty())
                            View.VISIBLE else View.GONE
                    binding.progressSearch.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchCompletedEvent.collect {
                    pendingScrollToTop = true
                }
            }
        }

        // On supprime l'observation de scrollToTop qui est maintenant gérée par le callback submitList

        // Recherche partagée (Personnes -> Recherche)
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

    private fun getTargetSize(spanCount: Int): Int {
        val screenWidth = requireContext().resources.displayMetrics.widthPixels
        return screenWidth / (spanCount * 2)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeGridColumns() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsCache.gridColumnsFlow.collect { columns ->
                    if (columns != gridLayoutManager.spanCount) {
                        adapter.targetSize = getTargetSize(columns)
                        gridLayoutManager.spanCount = columns
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    /** Chips retirables des filtres reconnus par le QueryParser (date, lieu). */
    private fun renderFilterChips(state: SearchViewModel.UiState) {
        binding.chipsFilters.removeAllViews()
        state.dateChip?.let { label ->
            binding.chipsFilters.addView(makeChip(label) { viewModel.removeDateFilter() })
        }
        state.geoChip?.let { label ->
            binding.chipsFilters.addView(makeChip(label) { viewModel.removeGeoFilter() })
        }
        state.typeChip?.let { label ->
            binding.chipsFilters.addView(makeChip(label) { viewModel.removeTypeFilter() })
        }
        state.personChips.forEach { person ->
            binding.chipsFilters.addView(
                makeChip(person.label) { viewModel.removePersonFilter(person.personId) }
            )
        }
        binding.chipsFilters.visibility =
            if (binding.chipsFilters.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun makeChip(label: String, onClose: () -> Unit): Chip =
        Chip(requireContext()).apply {
            text = label
            isCloseIconVisible = true
            isClickable = false
            setOnCloseIconClickListener { onClose() }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
