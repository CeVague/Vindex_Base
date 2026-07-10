package com.cevague.vindex.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Détail d'une personne : la liste de ses photos (via `faces`), le nom en titre.
 * Écran volontairement indépendant de l'album (futures actions propres à la
 * personne : suppression d'une photo de la fiche, recherche depuis la personne).
 */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    photoRepository: PhotoRepository,
    private val personRepository: PersonRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: Long = savedStateHandle[PersonDetailFragment.ARG_PERSON_ID] ?: -1L

    val photos: StateFlow<List<PhotoSummary>> =
        photoRepository.getPhotosSummaryByPerson(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    init {
        viewModelScope.launch {
            _title.value = personRepository.getPersonByIdOnce(personId)?.name.orEmpty()
        }
    }
}
