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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Photo>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.trim().length < 2) flowOf(emptyList())
            else repository.searchByFileName(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateQuery(newQuery: String) {
        _searchQuery.value = newQuery
    }
}