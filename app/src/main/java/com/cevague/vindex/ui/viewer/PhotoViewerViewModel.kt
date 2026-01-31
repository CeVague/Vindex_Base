package com.cevague.vindex.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<PhotoSummary>>(emptyList())
    val photos: StateFlow<List<PhotoSummary>> = _photos.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPhoto: StateFlow<Photo?> = _currentPosition
        .flatMapLatest { pos ->
            val photoId = _photos.value.getOrNull(pos)?.id
            if (photoId != null) {
                photoRepository.getPhotoById(photoId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setPhotos(list: List<PhotoSummary>, initialPosition: Int) {
        _photos.value = list
        _currentPosition.value = initialPosition
    }

    fun setPosition(position: Int) {
        if (_currentPosition.value != position) {
            _currentPosition.value = position
        }
    }
}