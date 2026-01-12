package com.cevague.vindex.ui.gallery

import androidx.lifecycle.*
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository

class GalleryViewModel(private val photoRepository: PhotoRepository) : ViewModel() {
    val allPhotos: LiveData<List<Photo>> = photoRepository.getVisiblePhotos().asLiveData()
}


class GalleryViewModelFactory(private val photoRepository: PhotoRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(photoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}