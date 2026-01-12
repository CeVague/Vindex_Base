package com.cevague.vindex.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.databinding.FragmentGalleryBinding

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

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

        val app = requireActivity().application as VindexApplication
        val repository = app.photoRepository

        val factory = GalleryViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[GalleryViewModel::class.java]

        val photoAdapter = PhotoAdapter()

        binding.recyclerGallery.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerGallery.adapter = photoAdapter

        viewModel.allPhotos.observe(viewLifecycleOwner) { photos ->
            if (photos.isEmpty()) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.recyclerGallery.visibility = View.GONE
            } else {
                binding.textEmpty.visibility = View.GONE
                binding.recyclerGallery.visibility = View.VISIBLE
                photoAdapter.submitList(photos)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}