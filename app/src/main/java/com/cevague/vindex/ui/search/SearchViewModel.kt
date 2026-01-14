// C:/Users/Administrateur/AndroidStudioProjects/Vindex/app/src/main/java/com/cevague/vindex/ui/search/SearchViewModel.ktpackage com.cevague.vindex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: PhotoRepository) : ViewModel() {

    // On remplace le flux réactif par une recherche déclenchée manuellement
    private val _searchTrigger = MutableSharedFlow<String>(replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Photo>> = _searchTrigger
        .flatMapLatest { query ->
            if (query.trim().length < 2) flowOf(emptyList())
            else repository.searchByFileName(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun performSearch(query: String) {
        _searchTrigger.tryEmit(query)
    }
}