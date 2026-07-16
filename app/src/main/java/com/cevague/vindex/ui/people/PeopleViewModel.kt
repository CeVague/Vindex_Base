package com.cevague.vindex.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.ai.MergeProposal
import com.cevague.vindex.ai.PersonCentroid
import com.cevague.vindex.ai.proposeMerges
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.search.asFloatArray
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Une proposition de fusion, prête à afficher : les deux visages et les deux noms. */
data class MergeSuggestion(
    val keepFace: FaceDao.FaceWithPhoto,
    val keepName: String?,
    val mergeFace: FaceDao.FaceWithPhoto,
    val mergeName: String?,
    val proposal: MergeProposal
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PersonRepository,
    private val settingsCache: SettingsCache
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val allPeople = settingsCache.showHiddenPeopleFlow
        .flatMapLatest { repository.getPeopleForTrombinoscope(includeHidden = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unnamedCount = repository.getUnnamedPersonCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Ce que la file a réellement à demander : les **groupes anonymes** à nommer, plus
     * les visages `pending` à confirmer.
     *
     * Comptait autrefois les seuls `pending` — et c'est pour ça que l'app ne demandait
     * **jamais** rien : un `pending` n'existe que si la personne la plus ressemblante
     * est **nommée**, donc sur une galerie neuve il n'y en a aucun. Le bouton restait
     * caché, la file vide, et le nommage n'était atteignable que par un clic long sur
     * un groupe — que rien n'indiquait.
     */
    val unidentifiedFaceCount: StateFlow<Int> = combine(
        repository.getGroupsToNameCount(),
        repository.getPendingFaceCount()
    ) { groups, pending -> groups + pending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Paires refusées, **le temps de la session** : refuser A+B ne doit pas empêcher
     * de proposer A+C, d'où une clé par paire et non par personne. Rien n'est
     * persisté — un refus revient donc au prochain lancement.
     */
    private val dismissed = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())

    /**
     * La meilleure fusion proposable, recalculée à chaque changement de la liste
     * (une fusion acceptée fait donc apparaître la suivante toute seule).
     *
     * `mapLatest` annule le calcul en cours quand une nouvelle émission arrive :
     * pendant un scan, la liste bouge sans arrêt et seul le dernier calcul compte.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val mergeSuggestion: StateFlow<MergeSuggestion?> =
        combine(repository.getPeopleForTrombinoscope(includeHidden = false), dismissed) { _, refused -> refused }
            .mapLatest { refused -> bestSuggestion(refused) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun bestSuggestion(refused: Set<Pair<Long, Long>>): MergeSuggestion? {
        // Un inconnu masqué ne fait l'objet d'aucune question : on ne demande pas si
        // deux personnes dont on a dit se moquer sont la même.
        val centroids = repository.getAllPersonsOnce().filterNot { it.isHidden }.mapNotNull { person ->
            person.centroidEmbedding?.let { blob ->
                PersonCentroid(
                    personId = person.id,
                    centroid = blob.asFloatArray(blob.size / Float.SIZE_BYTES),
                    named = person.name != null,
                    photoCount = person.photoCount
                )
            }
        }

        // Même politique qu'en recherche : en mode debug, aucun plancher. Un seuil
        // qui filtre les propositions les rend invisibles — impossible de voir qu'il
        // est trop haut, donc impossible de le calibrer. Sans plancher, les paires
        // défilent par similarité décroissante et la frontière se lit entre le
        // dernier « oui » et le premier « non ».
        val floor = if (settingsCache.showScores) {
            Float.NEGATIVE_INFINITY
        } else {
            settingsCache.faceThresholdNew
        }

        val proposal = proposeMerges(centroids, floor)
            .firstOrNull { pairKey(it) !in refused }
            ?: return null

        // Le visage de couverture est la seule chose qui rende la question lisible :
        // sans lui, on demanderait de fusionner deux numéros.
        val keepFace = repository.getPrimaryFaceWithPhoto(proposal.keepId) ?: return null
        val mergeFace = repository.getPrimaryFaceWithPhoto(proposal.mergeId) ?: return null

        return MergeSuggestion(
            keepFace = keepFace,
            keepName = repository.getPersonByIdOnce(proposal.keepId)?.name,
            mergeFace = mergeFace,
            mergeName = repository.getPersonByIdOnce(proposal.mergeId)?.name,
            proposal = proposal
        )
    }

    fun acceptMerge(suggestion: MergeSuggestion) {
        viewModelScope.launch {
            repository.mergePersons(
                keepId = suggestion.proposal.keepId,
                mergeId = suggestion.proposal.mergeId
            )
        }
    }

    fun dismissMerge(suggestion: MergeSuggestion) {
        dismissed.value = dismissed.value + pairKey(suggestion.proposal)
    }

    /** Clé indépendante du sens : la paire refusée l'est quel que soit qui garde l'identité. */
    private fun pairKey(proposal: MergeProposal): Pair<Long, Long> =
        minOf(proposal.keepId, proposal.mergeId) to maxOf(proposal.keepId, proposal.mergeId)
}
