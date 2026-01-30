package com.cevague.vindex.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.local.FastSettings
import com.cevague.vindex.databinding.FragmentGalleryBinding
import com.cevague.vindex.ui.viewer.PhotoViewerActivity
import com.cevague.vindex.ui.viewer.PhotoViewerNavData
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels {
        val app = requireActivity().application as VindexApplication
        GalleryViewModelFactory(app.photoRepository, PhotoGrouper(requireContext()))
    }

    private lateinit var adapter: GalleryAdapter

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
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter { photo, adapterPosition ->
            openPhotoViewer(adapterPosition)
        }

        val spanCount = FastSettings.gridColumns

        val layoutManager = GridLayoutManager(requireContext(), spanCount)

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    GalleryAdapter.VIEW_TYPE_HEADER -> spanCount  // Toute la largeur
                    GalleryAdapter.VIEW_TYPE_PHOTO -> 1           // 1 colonne
                    else -> 1
                }
            }
        }

        binding.recyclerGallery.layoutManager = layoutManager
        binding.recyclerGallery.adapter = adapter

        binding.recyclerGallery.setHasFixedSize(true)
        binding.recyclerGallery.setItemViewCacheSize(20)

        val app = requireActivity().application as VindexApplication

        binding.swipeRefreshGallery.setOnRefreshListener {
            lifecycleScope.launch {
                app.startGalleryScan()
                binding.swipeRefreshGallery.isRefreshing = false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.galleryItems.collect { items ->
                        adapter.submitList(items)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
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

    private fun openPhotoViewer(adapterPosition: Int) {
        val photoIndex = adapter.getPhotoIndex(adapterPosition)

        val photos = adapter.getPhotosOnly()

        if (photos.isEmpty() || photoIndex >= photos.size) return

        PhotoViewerNavData.currentList = photos

        PhotoViewerActivity.start(requireContext(), photos, photoIndex)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // IMPORTANT : Évite les memory leaks
        _binding = null
    }
}