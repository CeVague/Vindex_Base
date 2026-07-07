package com.cevague.vindex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.search.SearchPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pipeline: SearchPipeline
) : ViewModel() {

    data class PersonChip(val personId: Long, val label: String)

    data class UiState(
        val results: List<PhotoSummary> = emptyList(),
        val scores: Map<Long, Float> = emptyMap(),
        val dateChip: String? = null,
        val geoChip: String? = null,
        val typeChip: String? = null,
        val personChips: List<PersonChip> = emptyList(),
        val hasSearched: Boolean = false,
        val isLoading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchCompletedEvent = MutableSharedFlow<Unit>()
    val searchCompletedEvent: SharedFlow<Unit> = _searchCompletedEvent.asSharedFlow()

    private var rawQuery = ""
    private var useDateFilter = true
    private var useGeoFilter = true
    private var useTypeFilter = true
    private val removedPersonIds = mutableSetOf<Long>()
    private var searchJob: Job? = null

    fun performSearch(query: String) {
        rawQuery = query
        useDateFilter = true
        useGeoFilter = true
        useTypeFilter = true
        removedPersonIds.clear()
        runSearch()
    }

    fun onSearchFocused() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            pipeline.preload()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun removeDateFilter() {
        useDateFilter = false
        runSearch()
    }

    fun removeGeoFilter() {
        useGeoFilter = false
        runSearch()
    }

    fun removeTypeFilter() {
        useTypeFilter = false
        runSearch()
    }

    fun removePersonFilter(personId: Long) {
        removedPersonIds.add(personId)
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        if (rawQuery.isBlank()) {
            _uiState.value = UiState()
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = pipeline.search(
                rawQuery, useDateFilter, useGeoFilter, useTypeFilter, removedPersonIds
            )
            // On émet l'événement AVANT de mettre à jour l'état pour que le Fragment soit prêt
            _searchCompletedEvent.emit(Unit)

            _uiState.value = UiState(
                results = result.photos,
                scores = result.scores,
                dateChip = result.parsed.dateRange?.sourceText,
                geoChip = result.parsed.geoFilter?.sourceText,
                typeChip = result.parsed.typeFilter?.sourceText,
                personChips = result.parsed.persons.map {
                    PersonChip(it.personId, it.sourceText)
                },
                hasSearched = true,
                isLoading = false
            )
            _searchCompletedEvent.emit(Unit)
        }
    }
}
