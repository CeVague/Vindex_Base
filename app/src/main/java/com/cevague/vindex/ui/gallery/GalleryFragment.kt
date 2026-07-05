package com.cevague.vindex.ui.gallery

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.FragmentGalleryBinding
import com.cevague.vindex.ui.viewer.PhotoViewerActivity
import com.cevague.vindex.ui.viewer.ViewerSource
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels()

    @Inject
    lateinit var scanManager: ScanManager

    @Inject
    lateinit var settingsCache: SettingsCache
    private lateinit var adapter: GalleryAdapter
    private lateinit var gridLayoutManager: GridLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        observeGridColumns()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(getTargetSize(settingsCache.gridColumns)) { photo, _ ->
            PhotoViewerActivity.start(
                requireContext(),
                ViewerSource.Gallery(startPhotoId = photo.id)
            )
        }

        gridLayoutManager = GridLayoutManager(requireContext(), settingsCache.gridColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == GalleryAdapter.VIEW_TYPE_HEADER) {
                        gridLayoutManager.spanCount
                    } else {
                        1
                    }
                }
            }

            spanSizeLookup.isSpanIndexCacheEnabled = true
            spanSizeLookup.isSpanGroupIndexCacheEnabled = true
        }

        binding.recyclerGallery.apply {
            this.adapter = this@GalleryFragment.adapter
            this.layoutManager = gridLayoutManager

            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        binding.swipeRefreshGallery.setOnRefreshListener {
            lifecycleScope.launch {
                scanManager.startGalleryScan()
                binding.swipeRefreshGallery.isRefreshing = false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.galleryItems.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }

                launch {
                    adapter.loadStateFlow.collectLatest { loadStates ->
                        val refresh = loadStates.refresh
                        when (refresh) {
                            is LoadState.Loading -> renderState(GalleryUiState.Loading)
                            is LoadState.Error -> renderState(
                                GalleryUiState.Error(
                                    refresh.error.message ?: ""
                                )
                            )

                            is LoadState.NotLoading -> {
                                if (adapter.itemCount == 0) renderState(GalleryUiState.Empty)
                                else renderState(GalleryUiState.Success)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getTargetSize(spanCount: Int): Int {
        val screenWidth = requireContext().resources.displayMetrics.widthPixels
        return screenWidth / spanCount
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeGridColumns() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsCache.gridColumnsFlow.collect { columns ->
                    if (columns != gridLayoutManager.spanCount) {
                        adapter.targetSize = getTargetSize(columns)
                        gridLayoutManager.spanCount = columns
                        gridLayoutManager.spanSizeLookup.invalidateSpanIndexCache()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun renderState(state: GalleryUiState) {
        // when exhaustif grâce à la sealed class !
        when (state) {
            is GalleryUiState.Loading -> {
                binding.textEmpty.visibility = View.GONE
                binding.recyclerGallery.visibility = View.VISIBLE
                //binding.progressBar.isVisible = true
                //binding.recyclerView.isVisible = false
                // binding.emptyLayout?.isVisible = false
                // binding.errorLayout?.isVisible = false
            }

            is GalleryUiState.Success -> {
                binding.textEmpty.visibility = View.GONE
                binding.recyclerGallery.visibility = View.VISIBLE
                //binding.progressBar.isVisible = false
                //binding.recyclerView.isVisible = true
            }

            is GalleryUiState.Empty -> {
                binding.textEmpty.visibility = View.VISIBLE
                binding.recyclerGallery.visibility = View.GONE
            }

            is GalleryUiState.Error -> {
                binding.textEmpty.visibility = View.VISIBLE
                binding.recyclerGallery.visibility = View.GONE
                // binding.errorLayout?.isVisible = true
                // binding.errorText?.text = state.message
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // IMPORTANT : Évite les memory leaks
        _binding = null
    }
}