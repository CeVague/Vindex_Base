package com.cevague.vindex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(private val repository: PhotoRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val searchResults: StateFlow<List<PhotoSummary>> = query
        .flatMapLatest { q ->
            if (q.trim().length < 2) flowOf(emptyList())
            else repository.searchByFileNameSummary(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun performSearch(query: String) {
        this.query.value = query
    }
}
