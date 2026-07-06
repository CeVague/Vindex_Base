package com.cevague.vindex.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.AiModelRepository
import com.cevague.vindex.data.repository.ModelImportException
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
    private val repository: AiModelRepository
) : ViewModel() {

    sealed class Event {
        data class ImportSuccess(val modelName: String) : Event()
        data class ImportFailure(val reason: ModelImportException.Reason, val detail: String?) : Event()
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
                _events.emit(Event.ImportSuccess(model.modelName))
            } catch (e: ModelImportException) {
                _events.emit(Event.ImportFailure(e.reason, e.detail))
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun activate(model: AiModel) {
        viewModelScope.launch { repository.activate(model) }
    }

    fun delete(model: AiModel) {
        viewModelScope.launch { repository.delete(model) }
    }
}
