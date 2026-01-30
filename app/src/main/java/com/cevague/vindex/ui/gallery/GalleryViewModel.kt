package com.cevague.vindex.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.repository.PhotoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val photoRepository: PhotoRepository,
    private val photoGrouper: PhotoGrouper
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoSummary>>(emptyList())
    val photos: StateFlow<List<PhotoSummary>> = _photos.asStateFlow()

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading

            photoRepository.getVisiblePhotosSummary()
                .catch { e ->
                    _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error")
                }
                .collect { photos ->
                    _photos.value = photos

                    val groupedItems = photoGrouper.groupByDate(photos)
                    _galleryItems.value = groupedItems

                    _uiState.value = if (photos.isEmpty()) {
                        GalleryUiState.Empty
                    } else {
                        GalleryUiState.Success
                    }
                }
        }
    }

    fun refresh() {
        loadPhotos()
    }
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    object Success : GalleryUiState()
    object Empty : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}