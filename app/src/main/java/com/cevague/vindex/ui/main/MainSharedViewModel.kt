package com.cevague.vindex.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État partagé entre MainActivity et ses fragments. L'ancienne plomberie
 * recherche-depuis-un-onglet (`searchQuery`, `navigateToTab`) a été retirée avec
 * son dernier émetteur ; elle se réintroduira avec le raccourci « rechercher à
 * partir de cette personne » prévu au backlog.
 */
class MainSharedViewModel : ViewModel() {

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
}
