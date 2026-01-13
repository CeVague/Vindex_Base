package com.cevague.vindex.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cevague.vindex.data.repository.PhotoRepository

class PhotoViewerViewModelFactory(private val photoRepository: PhotoRepository) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhotoViewerViewModel::class.java)) {
            return PhotoViewerViewModel(photoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}