package com.cevague.vindex.ui.gallery

import androidx.lifecycle.*
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository

class GalleryViewModel(private val photoRepository: PhotoRepository) : ViewModel() {
    val allPhotos: LiveData<List<Photo>> = photoRepository.getVisiblePhotos().asLiveData()
}