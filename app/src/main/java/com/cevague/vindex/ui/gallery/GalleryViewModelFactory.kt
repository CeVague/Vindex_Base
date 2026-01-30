package com.cevague.vindex.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cevague.vindex.data.repository.PhotoRepository

class GalleryViewModelFactory(
    private val repository: PhotoRepository,
    private val grouper: PhotoGrouper
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GalleryViewModel(repository, grouper) as T
    }
}