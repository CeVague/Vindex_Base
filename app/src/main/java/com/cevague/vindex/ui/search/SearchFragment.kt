package com.cevague.vindex.ui.search

import SearchViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.databinding.FragmentSearchBinding
import com.cevague.vindex.ui.gallery.PhotoAdapter
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as VindexApplication).photoRepository)
    }

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

        val adapter = PhotoAdapter { photo ->
            // Action au clic sur une photo (ex: ouvrir le visualiseur)
        }

        binding.recyclerSearch.apply {
            this.adapter = adapter
            this.layoutManager = GridLayoutManager(requireContext(), 3)
        }

        // Écouter la saisie de l'utilisateur
        binding.inputSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.performSearch(query ?: "")
                binding.inputSearch.clearFocus() // Ferme le clavier
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // On ne fait rien ici pour économiser les ressources
                return true
            }
        })

        // Observer les résultats de recherche
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { photos ->
                adapter.submitList(photos)
                updateUIState(photos)
            }
        }
    }

    private fun updateUIState(photos: List<Photo>) {
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
