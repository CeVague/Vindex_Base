package com.cevague.vindex.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(private val repository: PersonRepository) : ViewModel() {
    val allPeople = repository.getNamedPersonsWithCover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unnamedCount = repository.getUnnamedPersonCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unidentifiedFaceCount: StateFlow<Int> = repository.getPendingFaceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}