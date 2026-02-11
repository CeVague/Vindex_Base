package com.cevague.vindex.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.dao.toSummaryList
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<PhotoSummary>>(emptyList())
    val photos: StateFlow<List<PhotoSummary>> = _photos.asStateFlow()

    private val _initialIndex = MutableStateFlow(0)
    val initialIndex: StateFlow<Int> = _initialIndex.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private var initialized = false

    fun init(source: ViewerSource) {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            when (source) {
                is ViewerSource.Gallery -> loadGallery(source.startPhotoId)
                is ViewerSource.Search -> loadSearch(source.photoIds, source.startPhotoId)
                is ViewerSource.Album -> loadAlbum(source.albumId, source.startPhotoId)
            }
        }
    }

    private suspend fun loadGallery(startId: Long) {
        photoRepository.getVisiblePhotosSummary()
            .collect { photos ->
                _photos.value = photos
                val index = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
                _initialIndex.value = index
                _currentPosition.value = index
            }
    }

    private suspend fun loadSearch(ids: List<Long>, startId: Long) {
        // Query unique par IDs, pas de re-recherche IA
        photoRepository.getPhotosSummaryByIds(ids)
            .collect { photos ->
                _photos.value = photos
                val index = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
                _initialIndex.value = index
                _currentPosition.value = index
            }
    }

    private suspend fun loadAlbum(albumId: Long, startId: Long) {
        // Future implémentation
        albumRepository.getPhotosInAlbum(albumId)
            .collect { photos ->
                _photos.value = photos.toSummaryList()
                val index = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
                _initialIndex.value = index
                _currentPosition.value = index
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPhoto: StateFlow<Photo?> = combine(_currentPosition, _photos) { pos, list ->
        list.getOrNull(pos)?.id
    }.flatMapLatest { id ->
        if (id != null) photoRepository.getPhotoById(id) else flowOf(null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setPosition(position: Int) {
        _currentPosition.value = position
    }
}
