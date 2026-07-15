package com.cevague.vindex.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.AiModelRepository
import com.cevague.vindex.data.repository.ModelImportException
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val repository: AiModelRepository,
    private val scanManager: ScanManager
) : ViewModel() {

    sealed class Event {
        data class ImportSuccess(val model: AiModel) : Event()
        data class ImportFailure(val reason: ModelImportException.Reason, val detail: String?) :
            Event()

        data class ConfirmReindex(val model: AiModel) : Event()

        /** Détecteur ou embedder : les deux invalident tous les visages. */
        data class ConfirmFaceReanalysis(val model: AiModel) : Event()
    }

    val models: StateFlow<List<AiModel>> = repository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun importModel(folderUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val model = repository.importFromFolder(folderUri)
                _events.emit(Event.ImportSuccess(model))
            } catch (e: ModelImportException) {
                _events.emit(Event.ImportFailure(e.reason, e.detail))
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * Un modèle dont dépend un travail déjà calculé ne s'active jamais en silence :
     * on demande **avant** de toucher à quoi que ce soit. Les autres (traduction…)
     * ne périment rien et s'activent directement.
     */
    fun activate(model: AiModel) {
        if (model.isActive) return // Déjà actif, rien à faire

        viewModelScope.launch {
            when (model.modelType) {
                AiModel.TYPE_CLIP -> _events.emit(Event.ConfirmReindex(model))

                AiModel.TYPE_FACE_DETECTION, AiModel.TYPE_FACE_EMBEDDING ->
                    _events.emit(Event.ConfirmFaceReanalysis(model))

                else -> repository.activate(model)
            }
        }
    }

    fun requestReindex(model: AiModel) {
        viewModelScope.launch {
            // 1. Activer réellement le modèle
            repository.activate(model)
            // 2. Lancer la ré-indexation (qui supprimera les anciens vecteurs)
            scanManager.startClipReindexing()
        }
    }

    fun requestFaceReanalysis(model: AiModel) {
        viewModelScope.launch {
            repository.activate(model)
            scanManager.startFaceReanalysis()
        }
    }

    /** Indexation initiale après le premier modèle CLIP (sans suppression). */
    fun startInitialIndexing() {
        viewModelScope.launch { scanManager.startClipIndexing() }
    }

    fun delete(model: AiModel) {
        viewModelScope.launch { repository.delete(model) }
    }
}
