package com.cevague.vindex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainSharedViewModel : ViewModel() {
    private val _searchQuery = MutableStateFlow<String?>("")
    val searchQuery: StateFlow<String?> = _searchQuery

    /**
     * Libellé d'un chargement transitoire (non piloté par WorkManager, ex. modèle
     * de recherche ou requête) affiché dans la barre de progression du haut, ou
     * null pour la masquer. MainActivity fusionne ce signal avec l'avancement des
     * scans (le scan a priorité).
     */
    private val _transientLoading = MutableStateFlow<String?>(null)
    val transientLoading: StateFlow<String?> = _transientLoading.asStateFlow()

    fun setTransientLoading(label: String?) {
        _transientLoading.value = label
    }

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