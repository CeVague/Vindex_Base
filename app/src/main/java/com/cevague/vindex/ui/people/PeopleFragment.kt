package com.cevague.vindex.ui.people

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.FragmentPeopleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PeopleFragment : Fragment() {

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var personRepository: PersonRepository

    @Inject
    lateinit var settingsCache: SettingsCache
    private val viewModel: PeopleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PeopleAdapter(
            settingsCache = settingsCache,
            onPersonClick = { person ->
                findNavController().navigate(
                    R.id.action_people_to_personDetail,
                    bundleOf(PersonDetailFragment.ARG_PERSON_ID to person.id)
                )
            },
            onPersonLongClick = { person, anchorView ->
                showPersonOptionsMenu(person, anchorView)
            }
        )

        binding.recyclerPeople.apply {
            this.adapter = adapter
            this.layoutManager = GridLayoutManager(requireContext(), settingsCache.gridColumns)

            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        // Observer les personnes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allPeople.collect { people ->
                    binding.textEmpty.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE
                    adapter.submitList(people)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mergeSuggestion.collect { renderMergeSuggestion(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.unidentifiedFaceCount.collect { count ->
                    binding.fabIdentify.apply {
                        visibility = if (count > 0) View.VISIBLE else View.GONE
                        if (count > 0) {
                            text = resources.getQuantityString(
                                R.plurals.people_to_identify_count,
                                count,
                                count
                            )
                        }

                        setOnClickListener {
                            // Ouvrir le BottomSheet d'identification
                            IdentifyFaceBottomSheet().show(childFragmentManager, "identify")
                        }
                    }
                }
            }
        }
    }

    /**
     * Une seule proposition à la fois, la meilleure : répondre à celle-ci fait
     * apparaître la suivante (la liste ré-émet). Le score n'est affiché qu'en mode
     * debug, comme partout ailleurs.
     */
    private fun renderMergeSuggestion(suggestion: MergeSuggestion?) {
        binding.cardMerge.visibility = if (suggestion == null) View.GONE else View.VISIBLE
        if (suggestion == null) return

        loadFaceCrop(binding.imageMergeKeep, suggestion.keepFace)
        loadFaceCrop(binding.imageMergeOther, suggestion.mergeFace)

        val unknown = getString(R.string.people_unknown)
        val names = getString(
            R.string.people_merge_suggest_names,
            suggestion.keepName ?: unknown,
            suggestion.mergeName ?: unknown
        )
        binding.textMergeDetail.text = if (settingsCache.showScores) {
            getString(
                R.string.people_merge_suggest_detail,
                names,
                String.format(Locale.US, "%.2f", suggestion.proposal.similarity)
            )
        } else {
            names
        }

        binding.buttonMergeConfirm.setOnClickListener { viewModel.acceptMerge(suggestion) }
        binding.buttonMergeDismiss.setOnClickListener { viewModel.dismissMerge(suggestion) }
    }

    private fun loadFaceCrop(view: ImageView, face: FaceDao.FaceWithPhoto) {
        val output = view.layoutParams.width
        Glide.with(view)
            .load(face.filePath)
            .signature(ObjectKey(face.id))
            .override(FaceCropTransformation.sourceSizeFor(face, output))
            .transform(FaceCropTransformation(face, output))
            .placeholder(R.drawable.vector_peoples)
            .error(R.drawable.vector_peoples)
            .into(view)
    }

    private fun showPersonOptionsMenu(person: PersonDao.PersonWithCover, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_person_options, popup.menu)
        // Masquer et ré-afficher sont la même décision, prise dans un sens ou dans
        // l'autre : jamais les deux à la fois.
        popup.menu.findItem(R.id.action_hide).isVisible = !person.isHidden
        popup.menu.findItem(R.id.action_unhide).isVisible = person.isHidden

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

                R.id.action_hide -> {
                    setHidden(person, true)
                    true
                }

                R.id.action_unhide -> {
                    setHidden(person, false)
                    true
                }

                R.id.action_exclude -> {
                    showExcludeDialog(person)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /**
     * Masquer n'est pas une suppression : les visages restent, le groupe reste vivant
     * et continue d'absorber ses nouvelles apparitions. C'est précisément ce qui fait
     * qu'on ne le revoit plus — le supprimer le ferait revenir à chaque analyse.
     */
    private fun setHidden(person: PersonDao.PersonWithCover, hidden: Boolean) {
        lifecycleScope.launch {
            personRepository.setPersonHidden(person.id, hidden)
            val message = if (hidden) R.string.people_hidden_done else R.string.people_unhidden_done
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(person: PersonDao.PersonWithCover) {
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

    private fun handleRename(person: PersonDao.PersonWithCover, newName: String) {
        lifecycleScope.launch {
            // Vérifier si une personne avec ce nom existe déjà
            val existingPerson = personRepository.getPersonByName(newName)

            if (existingPerson != null && existingPerson.id != person.id) {
                // Proposer la fusion
                showMergeConfirmation(person, existingPerson)
            } else {
                // Simple renommage
                personRepository.updateName(person.id, newName)
            }
        }
    }

    private fun showMergeConfirmation(source: PersonDao.PersonWithCover, target: Person) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Fusionner ?")
            .setMessage("\"${target.name}\" existe déjà. Voulez-vous fusionner ${source.photoCount} photos avec cette personne ?")
            .setPositiveButton("Fusionner") { _, _ ->
                lifecycleScope.launch {
                    personRepository.mergePersons(keepId = target.id, mergeId = source.id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showDeleteConfirmation(person: PersonDao.PersonWithCover) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer ?")
            .setMessage("Supprimer \"${person.name}\" ? Les ${person.photoCount} visages associés seront remis en attente d'identification.")
            .setPositiveButton("Supprimer") { _, _ ->
                lifecycleScope.launch {
                    personRepository.deletePersonAndResetFaces(person.id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Écarter demande **pourquoi**, et pas seulement confirmation : l'effet est le
     * même — les visages sortent du jeu — mais la raison sert à mesurer. Une affiche
     * détectée à 0,9 n'est pas une erreur du détecteur, un chat à 0,65 en est une ;
     * les confondre pollue la calibration du score de détection.
     *
     * Sans retour, d'où la confirmation qui suit : aucun écran ne ré-affiche un visage
     * écarté, et une photo déjà analysée n'est jamais ré-analysée.
     */
    private fun showExcludeDialog(person: PersonDao.PersonWithCover) {
        val reasons = arrayOf(
            getString(R.string.people_exclude_not_a_person),
            getString(R.string.people_exclude_depiction)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.people_exclude_title)
            .setItems(reasons) { _, which ->
                val reason =
                    if (which == 0) Face.EXCLUDED_NOT_A_PERSON else Face.EXCLUDED_DEPICTION
                confirmExclude(person, reason, reasons[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmExclude(
        person: PersonDao.PersonWithCover,
        reason: String,
        label: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(label)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.people_exclude_message,
                    person.photoCount,
                    person.photoCount
                )
            )
            .setPositiveButton(R.string.people_exclude_confirm) { _, _ ->
                lifecycleScope.launch { personRepository.excludePerson(person.id, reason) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
