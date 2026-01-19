package com.cevague.vindex.ui.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository

class GalleryViewModel(private val photoRepository: PhotoRepository) : ViewModel() {
    val allPhotos: LiveData<List<PhotoSummary>> = photoRepository.getVisiblePhotosSummary().asLiveData()
}