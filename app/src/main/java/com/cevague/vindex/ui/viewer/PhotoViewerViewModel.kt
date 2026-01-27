package com.cevague.vindex.ui.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository

class PhotoViewerViewModel(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _photos = MutableLiveData<List<PhotoSummary>>()
    val photos: LiveData<List<PhotoSummary>> = _photos

    private val _currentPosition = MutableLiveData(0)
    val currentPosition: LiveData<Int> = _currentPosition

    val currentPhoto: LiveData<Photo?> = _currentPosition.switchMap { pos ->
        val photoId = _photos.value?.getOrNull(pos)?.id
        if (photoId != null) {
            photoRepository.getPhotoById(photoId).asLiveData()
        } else {
            MutableLiveData(null)
        }
    }

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