package com.cevague.vindex.ui.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.databinding.FragmentPeopleBinding
import kotlinx.coroutines.launch

class PeopleFragment : Fragment() {

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PeopleViewModel by viewModels {
        PeopleViewModelFactory((requireActivity().application as VindexApplication).personRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val personRepo = (requireActivity().application as VindexApplication).personRepository

        val adapter = PeopleAdapter(personRepo) { person ->
            // Action au clic (ex: ouvrir la galerie filtrée par cette personne)
        }

        binding.recyclerPeople.apply {
            this.adapter = adapter
            this.layoutManager = GridLayoutManager(requireContext(), 3)
        }

        // Observer les personnes
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPeople.collect { people ->
                binding.textEmpty.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(people)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unidentifiedFaceCount.collect { count ->
                binding.fabIdentify.apply {
                    visibility = if (count > 0) View.VISIBLE else View.GONE
                    text = if (count <= 1) {
                        binding.root.context.getString(R.string.people_to_identify_count_single)
                    } else {
                        binding.root.context.getString(R.string.people_to_identify_count, count)
                    }
                    setOnClickListener {
                        // Ouvrir le BottomSheet d'identification
                        IdentifyFaceBottomSheet().show(childFragmentManager, "identify")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}