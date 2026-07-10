package com.cevague.vindex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.search.SearchPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pipeline: SearchPipeline
) : ViewModel() {

    data class PersonChip(val personId: Long, val label: String)

    /** Nature du chargement en cours (barre de progression du haut). */
    enum class Loading { MODEL, SEARCH }

    data class UiState(
        val results: List<PhotoSummary> = emptyList(),
        val scores: Map<Long, Float> = emptyMap(),
        val dateChip: String? = null,
        val geoChip: String? = null,
        val countryChip: String? = null,
        val typeChip: String? = null,
        val personChips: List<PersonChip> = emptyList(),
        val hasSearched: Boolean = false,
        val loading: Loading? = null,
        /** Incrémenté à chaque nouvelle recherche : signale un jeu de résultats
         *  frais qui doit ramener la grille en haut (comparé côté fragment). */
        val generation: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var rawQuery = ""
    private var useDateFilter = true
    private var useGeoFilter = true
    private var useCountryFilter = true
    private var useTypeFilter = true
    private val removedPersonIds = mutableSetOf<Long>()
    private var searchJob: Job? = null
    private var generation = 0

    fun performSearch(query: String) {
        rawQuery = query
        useDateFilter = true
        useGeoFilter = true
        useCountryFilter = true
        useTypeFilter = true
        removedPersonIds.clear()
        runSearch()
    }

    fun onSearchFocused() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = Loading.MODEL) }
            pipeline.preload()
            _uiState.update { it.copy(loading = null) }
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

    fun removeCountryFilter() {
        useCountryFilter = false
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
        generation++
        val gen = generation
        if (rawQuery.isBlank()) {
            _uiState.value = UiState(generation = gen)
            return
        }
        searchJob = viewModelScope.launch {
            // On garde les résultats courants pendant le chargement (la grille ne
            // remonte qu'à la fin, sur l'émission finale ci-dessous).
            _uiState.update { it.copy(loading = Loading.SEARCH, generation = gen) }
            val result = pipeline.search(
                rawQuery, useDateFilter, useGeoFilter, useTypeFilter, removedPersonIds,
                useCountryFilter
            )
            _uiState.value = UiState(
                results = result.photos,
                scores = result.scores,
                dateChip = result.parsed.dateRange?.sourceText,
                geoChip = result.parsed.geoFilter?.sourceText,
                countryChip = result.parsed.countryFilter?.sourceText,
                typeChip = result.parsed.typeFilter?.sourceText,
                personChips = result.parsed.persons.map {
                    PersonChip(it.personId, it.sourceText)
                },
                hasSearched = true,
                loading = null,
                generation = gen
            )
        }
    }
}
