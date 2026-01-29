package com.cevague.vindex.ui.people

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.FragmentPeopleBinding
import com.cevague.vindex.ui.main.MainSharedViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PeopleFragment : Fragment() {

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    private lateinit var personRepo: PersonRepository


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

        personRepo = (requireActivity().application as VindexApplication).personRepository

        val adapter = PeopleAdapter(
            repository = personRepo,
            onPersonClick = { person ->
                navigateToSearchWithPerson(person)
            },
            onPersonLongClick = { person, anchorView ->
                showPersonOptionsMenu(person, anchorView)
            }
        )

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

    private fun navigateToSearchWithPerson(person: Person) {
        val query = "name:\"${person.name}\""
        val sharedViewModel: MainSharedViewModel by activityViewModels()

        sharedViewModel.triggerSearch(query)
        sharedViewModel.selectTab(R.id.searchFragment)
    }

    private fun showPersonOptionsMenu(person: Person, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_person_options, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_rename -> {
                    showRenameDialog(person)
                    true
                }

                R.id.action_delete -> {
                    showDeleteConfirmation(person)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(person: Person) {
        val editText = EditText(requireContext()).apply {
            setText(person.name)
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_CLASS_TEXT
            hint = "Nom de la personne"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Renommer")
            .setView(editText)
            .setPositiveButton("Renommer") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    handleRename(person, newName)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun handleRename(person: Person, newName: String) {
        lifecycleScope.launch {
            // Vérifier si une personne avec ce nom existe déjà
            val existingPerson = personRepo.getPersonByName(newName)

            if (existingPerson != null && existingPerson.id != person.id) {
                // Proposer la fusion
                showMergeConfirmation(person, existingPerson)
            } else {
                // Simple renommage
                personRepo.updateName(person.id, newName)
            }
        }
    }

    private fun showMergeConfirmation(source: Person, target: Person) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Fusionner ?")
            .setMessage("\"${target.name}\" existe déjà. Voulez-vous fusionner ${source.photoCount} photos avec cette personne ?")
            .setPositiveButton("Fusionner") { _, _ ->
                lifecycleScope.launch {
                    personRepo.mergePersons(keepId = target.id, mergeId = source.id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showDeleteConfirmation(person: Person) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer ?")
            .setMessage("Supprimer \"${person.name}\" ? Les ${person.photoCount} visages associés seront remis en attente d'identification.")
            .setPositiveButton("Supprimer") { _, _ ->
                lifecycleScope.launch {
                    // Remettre les visages en "pending"
                    // TODO: ajouter une méthode dans PersonRepository pour ça
                    personRepo.deleteById(person.id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
