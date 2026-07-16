package com.cevague.vindex.ui.people

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.DialogIdentifyFaceBinding
import com.cevague.vindex.search.QueryParser
import com.cevague.vindex.search.asFloatArray
import com.cevague.vindex.search.dotProduct
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class IdentifyFaceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogIdentifyFaceBinding? = null
    private val binding get() = _binding!!

    /**
     * Ce que la file demande. Deux questions distinctes, dans cet ordre :
     *
     * [Group] « qui est-ce ? » sur un **groupe** anonyme — c'est le nommage initial,
     * et il porte sur le groupe entier : répondre une fois nomme ses huit photos.
     *
     * [SingleFace] « est-ce bien Marie ? » sur un visage `pending` — une confirmation,
     * qui n'existe qu'une fois des personnes nommées.
     */
    private sealed interface Target {
        val face: FaceDao.FaceWithPhoto

        data class Group(
            val personId: Long,
            override val face: FaceDao.FaceWithPhoto,
            val photoCount: Int,
            val centroid: ByteArray?
        ) : Target

        data class SingleFace(override val face: FaceDao.FaceWithPhoto) : Target
    }

    /** Passés PENDANT cette session : rien n'est écrit en base. */
    private val skippedGroups = mutableSetOf<Long>()
    private val skippedFaces = mutableSetOf<Long>()

    private var current: Target? = null
    private var allPersons: List<Person> = emptyList()

    @Inject
    lateinit var personRepository: PersonRepository

    @Inject
    lateinit var settingsCache: SettingsCache

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
        setupButtons()
        setupPreview()
        loadNext()
    }

    private fun setupAutoComplete() {
        lifecycleScope.launch {
            personRepository.getAllPersons().collect { persons ->
                allPersons = persons
                binding.editName.setAdapter(nameAdapter(persons.mapNotNull { it.name }))
            }
        }

        binding.editName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.editName.text.toString().trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { identifyCurrentFaceAs(it) }
                true
            } else false
        }

        binding.editName.setOnItemClickListener { _, _, position, _ ->
            identifyCurrentFaceAs(binding.editName.adapter.getItem(position) as String)
        }

        binding.buttonValidate.setOnClickListener {
            binding.editName.text.toString().trim()
                .takeIf { it.isNotEmpty() }
                ?.let { identifyCurrentFaceAs(it) }
        }

        // Grisé tant que le champ est vide : un bouton qui ne peut rien faire ne doit
        // pas avoir l'air actionnable.
        binding.editName.doAfterTextChanged {
            binding.buttonValidate.isEnabled = !it.isNullOrBlank()
        }
    }

    /**
     * Autocomplétion insensible à la **casse et aux accents** : le filtre par défaut
     * d'`ArrayAdapter` ignore la casse mais pas les diacritiques, donc « gery » ne
     * trouvait jamais « Géry ».
     *
     * Réutilise `QueryParser.normalize` — la même normalisation que la recherche, et
     * délibérément la même : les deux répondent à « ce que l'utilisateur tape » contre
     * « ce qui est stocké ». En avoir deux définitions les ferait diverger en silence.
     */
    private fun nameAdapter(names: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, names.toMutableList()
        ) {
            private val all = names

            override fun getFilter(): Filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = QueryParser.normalize(constraint?.toString().orEmpty())
                    val matches = if (query.isEmpty()) all else all.filter {
                        QueryParser.normalize(it).contains(query)
                    }
                    return FilterResults().apply {
                        values = matches
                        count = matches.size
                    }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                    clear()
                    addAll(results.values as? List<String> ?: emptyList())
                    notifyDataSetChanged()
                }

                override fun convertResultToString(resultValue: Any?): CharSequence =
                    resultValue as? String ?: ""
            }
        }

    /**
     * Chaque action porte sur **tout le groupe** quand la file en sert un : écarter un
     * groupe de mains écarte ses huit visages d'un coup, et non un seul qui laisserait
     * les sept autres reposer la question.
     */
    private fun setupButtons() {
        binding.buttonSkip.setOnClickListener {
            when (val target = current) {
                is Target.Group -> skippedGroups += target.personId
                is Target.SingleFace -> skippedFaces += target.face.id
                null -> Unit
            }
            loadNext()
        }

        // Un clic, pas de sous-question : la nuance animal/dessin a servi à la
        // calibration, elle ne sert plus à l'usage.
        binding.buttonNotPerson.setOnClickListener {
            val target = current ?: return@setOnClickListener
            lifecycleScope.launch {
                when (target) {
                    is Target.Group ->
                        personRepository.excludePerson(target.personId, Face.EXCLUDED_IRRELEVANT)

                    is Target.SingleFace ->
                        personRepository.markAsIgnored(target.face.id, Face.EXCLUDED_IRRELEVANT)
                }
                loadNext()
            }
        }

        binding.buttonStranger.setOnClickListener {
            val target = current ?: return@setOnClickListener
            lifecycleScope.launch {
                when (target) {
                    // Le groupe existe déjà : il suffit de le masquer, et il continuera
                    // d'absorber ses futures apparitions sans jamais revenir à l'écran.
                    is Target.Group -> personRepository.setPersonHidden(target.personId, true)
                    is Target.SingleFace -> hideAsStranger(target.face)
                }
                loadNext()
            }
        }
    }

    /**
     * Appui long sur le visage → la photo entière, tant que le doigt reste.
     *
     * Le rond ne montre que le visage, or c'est souvent le **contexte** qui permet de
     * reconnaître quelqu'un (qui est à côté, où, quand). Sans aller-retour vers un
     * autre écran : la file d'identification est déjà assez longue comme ça.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupPreview() {
        binding.imageFace.setOnLongClickListener {
            val face = current?.face ?: return@setOnLongClickListener false
            Glide.with(this).load(face.filePath.toUri()).into(binding.imagePreview)
            binding.imagePreview.visibility = View.VISIBLE
            true
        }
        // Le relâchement doit fermer l'aperçu même si le doigt a glissé hors de la
        // vue : sans ACTION_CANCEL, l'aperçu resterait collé à l'écran.
        binding.imageFace.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                binding.imagePreview.visibility = View.GONE
            }
            v.onTouchEvent(event)
        }
    }

    /**
     * Les **groupes anonymes d'abord**, les confirmations ensuite : nommer un groupe
     * de huit photos répond à huit questions à la fois, alors qu'un `pending` n'en
     * règle qu'une — et les `pending` n'existent de toute façon qu'une fois des
     * personnes nommées.
     */
    private fun loadNext() {
        lifecycleScope.launch {
            val group = personRepository.getNextGroupToName(skippedGroups)
            current = when {
                group != null -> Target.Group(
                    personId = group.personId,
                    face = FaceDao.FaceWithPhoto(
                        id = group.faceId,
                        filePath = group.filePath,
                        boxLeft = group.boxLeft,
                        boxTop = group.boxTop,
                        boxRight = group.boxRight,
                        boxBottom = group.boxBottom
                    ),
                    photoCount = group.photoCount,
                    centroid = group.centroid
                )

                else -> personRepository.getNextPendingFaceExcluding(skippedFaces)
                    ?.let { Target.SingleFace(it) }
            }

            val target = current
            if (target == null) {
                dismiss()
                return@launch
            }
            displayFace(target.face)
            updateSuggestions(target)
            updateCounter(target)
        }
    }

    private fun displayFace(face: FaceDao.FaceWithPhoto) {
        val output = binding.imageFace.layoutParams.width
        Glide.with(this)
            .load(face.filePath)
            // Pas de fondu : on enchaîne des dizaines de visages, et une transition
            // fait ressembler chaque passage à une attente. Le cache disque, lui, est
            // gardé — un visage déjà vu doit revenir instantanément.
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .signature(ObjectKey(face.id))
            .override(FaceCropTransformation.sourceSizeFor(face, output))
            .transform(FaceCropTransformation(face, output))
            .into(binding.imageFace)

        binding.editName.text?.clear()
        binding.chipGroupSuggestions.clearCheck()
    }

    /**
     * Suggestions triées par **ressemblance au visage affiché**, et non par nombre de
     * photos.
     *
     * L'ancien tri (les 5 personnes les plus photographiées) proposait toujours les
     * mêmes noms, quel que soit le visage : l'information la plus utile — « ça
     * ressemble à Marie » — était précisément celle qui manquait, alors que l'app la
     * calcule déjà pour clusteriser.
     */
    private suspend fun updateSuggestions(target: Target) {
        // Lu à la demande, et non pris dans le champ alimenté par le Flow : celui-ci
        // est encore vide au premier visage (la collecte démarre en parallèle de
        // `loadNext`), et la toute première question s'affichait donc SANS aucune
        // suggestion — la course était invisible et arrivait à chaque ouverture.
        val persons = personRepository.getAllPersonsOnce()

        // Un groupe se compare par son centroïde — le résumé de tous ses visages —
        // plutôt que par le seul visage affiché : c'est plus fidèle et déjà calculé.
        val embedding = when (target) {
            is Target.Group -> target.centroid
            is Target.SingleFace -> personRepository.getFaceByIdOnce(target.face.id)?.embedding
        }
        val ranked = if (embedding == null) {
            // Sans vecteur, aucune ressemblance calculable : on retombe sur les plus
            // photographiées plutôt que de ne rien proposer.
            persons.filter { it.name != null && !it.isHidden }
                .sortedByDescending { it.photoCount }
                .take(MAX_SUGGESTIONS).map { it to null }
        } else {
            val vector = embedding.asFloatArray(embedding.size / Float.SIZE_BYTES)
            persons
                .filter { it.name != null && it.centroidEmbedding != null && !it.isHidden }
                .map { person ->
                    val centroid = person.centroidEmbedding!!
                    person to dotProduct(
                        vector, centroid.asFloatArray(centroid.size / Float.SIZE_BYTES)
                    )
                }
                .sortedByDescending { it.second }
                .take(MAX_SUGGESTIONS)
        }

        binding.chipGroupSuggestions.removeAllViews()
        val empty = ranked.isEmpty()
        binding.textSuggestionsLabel.visibility = if (empty) View.GONE else View.VISIBLE
        binding.scrollSuggestions.visibility = if (empty) View.GONE else View.VISIBLE

        for ((person, similarity) in ranked) {
            binding.chipGroupSuggestions.addView(suggestionChip(person, similarity))
        }
        binding.scrollSuggestions.scrollTo(0, 0)
    }

    /**
     * Puce = le nom seul.
     *
     * Elle a porté la tête de la personne, et c'était une erreur : à cette taille le
     * visage était illisible, tout en coûtant de la largeur — donc une liste qui
     * débordait — et un décodage par puce. Le nom seul en dit plus, en moins de place.
     */
    private fun suggestionChip(person: Person, similarity: Float?): Chip =
        Chip(requireContext()).apply {
            text = if (similarity != null && settingsCache.showScores) {
                String.format(Locale.US, "%s (%.2f)", person.name, similarity)
            } else {
                person.name
            }
            isCheckable = true
            setOnClickListener { identifyCurrentFaceAs(person.name!!) }
        }

    private fun updateCounter(target: Target) {
        lifecycleScope.launch {
            val remaining = when (target) {
                is Target.Group ->
                    personRepository.getGroupsToNameCount().first() - skippedGroups.size

                is Target.SingleFace ->
                    personRepository.getPendingFaceCount().first() - skippedFaces.size
            }
            binding.textCounter.text =
                resources.getQuantityString(R.plurals.people_remaining, remaining, remaining)
            // Sur un groupe, dire combien de photos sont en jeu : la réponse ne coûte
            // pas le même effort selon qu'elle en règle une ou huit.
            binding.textPhotoCount.apply {
                if (target is Target.Group && target.photoCount > 1) {
                    text = resources.getQuantityString(
                        R.plurals.people_photo_count, target.photoCount, target.photoCount
                    )
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
        }
    }

    /**
     * Un inconnu est une **personne**, pas un rebut : il lui faut donc un groupe, même
     * si on ne le regardera jamais. Masqué dès sa création, il absorbera ses futures
     * apparitions sans jamais remonter à l'écran.
     */
    private suspend fun hideAsStranger(face: FaceDao.FaceWithPhoto) {
        val personId = personRepository.createPerson()
        personRepository.assignFaceToPerson(
            faceId = face.id,
            personId = personId,
            assignmentType = Face.ASSIGNMENT_MANUAL,
            confidence = 1.0f,
            weight = 1.0f
        )
        personRepository.recomputeCentroid(personId)
        personRepository.setPersonHidden(personId, true)
    }

    private fun identifyCurrentFaceAs(name: String) {
        val target = current ?: return
        lifecycleScope.launch {
            when (target) {
                is Target.Group -> nameGroup(target, name)
                is Target.SingleFace -> nameSingleFace(target.face, name)
            }
            loadNext()
        }
    }

    /**
     * Nommer un groupe anonyme. Si le nom existe déjà, c'est une **fusion** et non un
     * renommage : deux groupes du même nom seraient deux personnes homonymes, donc un
     * bug. Le groupe nommé survit, l'anonyme y est versé.
     */
    private suspend fun nameGroup(target: Target.Group, name: String) {
        val existing = personRepository.getPersonByName(name)
        if (existing != null && existing.id != target.personId) {
            personRepository.mergePersons(keepId = existing.id, mergeId = target.personId)
        } else {
            personRepository.updateName(target.personId, name)
        }
        skippedGroups.remove(target.personId)
    }

    private suspend fun nameSingleFace(face: FaceDao.FaceWithPhoto, name: String) {
        val personId = personRepository.getOrCreatePersonByName(name)
        personRepository.assignFaceToPerson(
            faceId = face.id,
            personId = personId,
            assignmentType = Face.ASSIGNMENT_MANUAL,
            confidence = 1.0f,
            weight = 1.0f
        )
        // Sans ça, une personne nommée à la main n'a aucun centroïde : invisible pour
        // `assignFace` comme pour les propositions de fusion, qui écartent tous deux
        // les centroïdes nuls.
        personRepository.recomputeCentroid(personId)
        skippedFaces.remove(face.id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /**
         * Trois, et non cinq : sans vignette, trois noms tiennent dans la largeur d'un
         * téléphone. Au-delà, la liste débordait et il fallait la faire défiler pour
         * voir des suggestions de moins en moins ressemblantes — donc de moins en
         * moins utiles.
         */
        const val MAX_SUGGESTIONS = 3
    }
}
