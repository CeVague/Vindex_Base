package com.cevague.vindex.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.dao.toSummaryList
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.search.SearchSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository,
    private val searchSessionRepository: SearchSessionRepository
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
                is ViewerSource.Search -> loadSearch(source.sessionId, source.startPhotoId)
                is ViewerSource.Album -> loadAlbum(source.albumId, source.startPhotoId)
                is ViewerSource.Folder -> loadFolder(source.folderPath, source.startPhotoId)
            }
        }
    }

    private suspend fun loadGallery(startId: Long) {
        val photos = photoRepository.getVisiblePhotosSummary().first()
        val index = photos.indexOfFirst { it.id == startId }
        _initialIndex.value = if (index >= 0) index else 0
        _photos.value = photos
    }

    private suspend fun loadSearch(sessionId: String, startId: Long) {
        // Session absente (évincée LRU ou process recréé à froid) : repli sur la
        // seule photo tapée, dont l'id survit dans l'Intent.
        val ids = searchSessionRepository.get(sessionId) ?: listOf(startId)
        val photos = photoRepository.getPhotosSummaryByIdsOrdered(ids)
        val index = photos.indexOfFirst { it.id == startId }
        _initialIndex.value = if (index >= 0) index else 0
        _photos.value = photos
    }

    private suspend fun loadAlbum(albumId: Long, startId: Long) {
        val photos = albumRepository.getPhotosInAlbum(albumId).first().toSummaryList()
        val index = photos.indexOfFirst { it.id == startId }
        _initialIndex.value = if (index >= 0) index else 0
        _photos.value = photos
    }

    private suspend fun loadFolder(folderPath: String, startId: Long) {
        val photos = photoRepository.getPhotosSummaryByFolderOnce(folderPath)
        val index = photos.indexOfFirst { it.id == startId }
        _initialIndex.value = if (index >= 0) index else 0
        _photos.value = photos
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
