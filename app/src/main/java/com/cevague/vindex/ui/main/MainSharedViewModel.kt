package com.cevague.vindex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainSharedViewModel : ViewModel() {
    private val _searchQuery = MutableStateFlow<String?>("")
    val searchQuery: StateFlow<String?> = _searchQuery

    fun triggerSearch(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = null
    }

    // Le signal pour changer d'onglet
    private val _navigateToTab = MutableSharedFlow<Int>(replay = 0)
    val navigateToTab = _navigateToTab.asSharedFlow()

    fun selectTab(menuItemId: Int) {
        viewModelScope.launch { _navigateToTab.emit(menuItemId) }
    }
}