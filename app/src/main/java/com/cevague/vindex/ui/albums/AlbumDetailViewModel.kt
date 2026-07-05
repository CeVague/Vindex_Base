package com.cevague.vindex.ui.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.dao.toSummaryList
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.ui.viewer.ViewerSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Détail d'un album : dossier virtuel (folderPath) ou album matérialisé (albumId). */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    photoRepository: PhotoRepository,
    albumRepository: AlbumRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val folderPath: String = savedStateHandle[AlbumDetailFragment.ARG_FOLDER_PATH] ?: ""
    private val albumId: Long = savedStateHandle[AlbumDetailFragment.ARG_ALBUM_ID] ?: -1L
    private val isAlbum: Boolean = albumId >= 0

    val photos: StateFlow<List<PhotoSummary>> =
        (if (isAlbum) albumRepository.getPhotosInAlbum(albumId).map { it.toSummaryList() }
        else photoRepository.getPhotosSummaryByFolder(folderPath))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _title = MutableStateFlow(
        if (isAlbum) "" else folderPath.substringAfterLast('/').ifEmpty { folderPath }
    )
    val title: StateFlow<String> = _title.asStateFlow()

    init {
        if (isAlbum) viewModelScope.launch {
            _title.value = albumRepository.getAlbumByIdOnce(albumId)?.name.orEmpty()
        }
    }

    fun viewerSource(startPhotoId: Long): ViewerSource =
        if (isAlbum) ViewerSource.Album(albumId, startPhotoId)
        else ViewerSource.Folder(folderPath, startPhotoId)
}
