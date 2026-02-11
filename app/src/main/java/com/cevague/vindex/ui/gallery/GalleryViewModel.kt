package com.cevague.vindex.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val photoGrouper: PhotoGrouper
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val galleryItems: Flow<PagingData<GalleryItem>> =
        photoRepository.getVisiblePhotosSummaryPaged()
            .map { pagingData ->
                pagingData
                    .map<PhotoSummary, GalleryItem> { GalleryItem.PhotoItem(it) }
                    .insertSeparators { before, after ->
                        computeHeader(before, after)
                    }
            }
            .cachedIn(viewModelScope)

    private fun computeHeader(
        before: GalleryItem?,
        after: GalleryItem?
    ): GalleryItem.Header? {
        if (after !is GalleryItem.PhotoItem) return null
        val afterDate = after.photo.dateTaken ?: return null

        if (before == null) {
            // Premier élément → toujours un header
            return photoGrouper.makeHeader(afterDate)
        }

        if (before !is GalleryItem.PhotoItem) return null
        val beforeDate = before.photo.dateTaken ?: return photoGrouper.makeHeader(afterDate)

        // Comparer les groupes temporels
        val beforeGroup = photoGrouper.makeHeader(beforeDate)
        val afterGroup = photoGrouper.makeHeader(afterDate)

        return if (beforeGroup != afterGroup) photoGrouper.makeHeader(afterDate) else null
    }
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    object Success : GalleryUiState()
    object Empty : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}