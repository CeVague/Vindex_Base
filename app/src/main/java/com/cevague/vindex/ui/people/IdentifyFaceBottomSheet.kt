package com.cevague.vindex.ui.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.DialogIdentifyFaceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IdentifyFaceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogIdentifyFaceBinding? = null
    private val binding get() = _binding!!

    private var currentIndex = 0

    // IDs des visages skippés PENDANT cette session
    private val skippedIds = mutableSetOf<Long>()

    // Visage actuellement affiché
    private var currentFace: FaceDao.FaceWithPhoto? = null

    // Liste des personnes pour l'auto-complétion
    private var allPersons: List<Person> = emptyList()

    @Inject
    lateinit var personRepository: PersonRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIdentifyFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAutoComplete()
        setupSuggestionChips()
        setupButtons()
        loadNextFace()
    }

    private fun setupAutoComplete() {
        // 1. Observer les personnes existantes
        lifecycleScope.launch {
            personRepository.getAllPersons().collect { persons ->
                allPersons = persons
                // 2. Créer l'adapter pour l'AutoCompleteTextView
                val names = persons.mapNotNull { it.name }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    names
                )
                binding.editName.setAdapter(adapter)

                // Mettre à jour les suggestions quand les données arrivent
                updateSuggestionChips(persons)
            }
        }

        // 3. Validation quand on appuie sur "Done" ou sélectionne une suggestion
        binding.editName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = binding.editName.text.toString().trim()
                if (name.isNotEmpty()) {
                    identifyCurrentFaceAs(name)
                }
                true
            } else false
        }

        binding.editName.setOnItemClickListener { _, _, position, _ ->
            val name = binding.editName.adapter.getItem(position) as String
            identifyCurrentFaceAs(name)
        }
    }

    private fun setupSuggestionChips() {
        // Cette méthode est maintenant appelée depuis setupAutoComplete pour garantir que les données sont prêtes
    }

    private fun updateSuggestionChips(persons: List<Person>) {
        // Afficher les 5 personnes avec le plus de photos
        val topPersons = persons
            .filter { it.name != null }
            .sortedByDescending { it.photoCount }
            .take(5)

        binding.chipGroupSuggestions.removeAllViews()

        if (topPersons.isEmpty()) {
            binding.textSuggestionsLabel.visibility = View.GONE
            binding.chipGroupSuggestions.visibility = View.GONE
        } else {
            binding.textSuggestionsLabel.visibility = View.VISIBLE
            binding.chipGroupSuggestions.visibility = View.VISIBLE

            topPersons.forEach { person ->
                val chip = Chip(requireContext()).apply {
                    text = person.name
                    isCheckable = true
                    setOnClickListener {
                        identifyCurrentFaceAs(person.name!!)
                    }
                }
                binding.chipGroupSuggestions.addView(chip)
            }
        }
    }

    private fun setupButtons() {
        binding.buttonSkip.setOnClickListener {
            currentFace?.let { face ->
                skippedIds.add(face.id)  // Mémorise localement, reste "pending" en DB
            }
            loadNextFace()
        }

        binding.buttonNotPerson.setOnClickListener {
            currentFace?.let { face ->
                lifecycleScope.launch {
                    personRepository.markAsIgnored(face.id)  // Marque comme "ignored" en DB
                    loadNextFace()
                }
            }
        }
    }

    private fun loadNextFace() {
        lifecycleScope.launch {
            // Récupère le prochain visage pending en excluant les skippés
            currentFace = personRepository.getNextPendingFaceExcluding(skippedIds)

            if (currentFace == null) {
                dismiss()  // Plus rien à traiter
                return@launch
            }

            // Afficher le visage
            displayFace(currentFace!!)

            // Mettre à jour le compteur
            updateCounter()
        }
    }


    private fun displayFace(face: FaceDao.FaceWithPhoto) {
        val output = binding.imageFace.layoutParams.width
        Glide.with(this)
            .load(face.filePath)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .override(FaceCropTransformation.sourceSizeFor(face, output))
            .transform(FaceCropTransformation(face, output))
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.imageFace)

        // Reset du champ texte
        binding.editName.text?.clear()
        binding.chipGroupSuggestions.clearCheck()
    }

    private fun updateCounter() {
        lifecycleScope.launch {
            val totalPending = personRepository.getPendingFaceCount().first()
            val remaining = totalPending - skippedIds.size

            binding.textCounter.text =
                resources.getQuantityString(R.plurals.people_remaining, remaining, remaining)
        }
    }

    private fun identifyCurrentFaceAs(name: String) {
        val face = currentFace ?: return

        lifecycleScope.launch {
            // 1. Créer ou récupérer la personne
            val personId = personRepository.getOrCreatePersonByName(name)

            // 2. Assigner le visage
            personRepository.assignFaceToPerson(
                faceId = face.id,
                personId = personId,
                assignmentType = Face.ASSIGNMENT_MANUAL,
                confidence = 1.0f,
                weight = 1.0f
            )

            // 3. Sans ça, une personne nommée à la main n'a **aucun centroïde** : elle
            // est alors invisible pour `assignFace` comme pour les propositions de
            // fusion, qui écartent toutes deux les centroïdes nuls. Nommer quelqu'un
            // n'aurait donc appris strictement rien à l'automate — et c'est justement
            // la seule vérité terrain dont il dispose.
            personRepository.recomputeCentroid(personId)

            // 4. Retirer des skippés si jamais il y était (ne devrait pas arriver)
            skippedIds.remove(face.id)

            loadNextFace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}