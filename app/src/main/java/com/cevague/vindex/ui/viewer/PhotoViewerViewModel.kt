package com.cevague.vindex.ui.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.repository.PhotoRepository

class PhotoViewerViewModel(private val photoRepository: PhotoRepository) : ViewModel() {

    val allPhotos: LiveData<List<Photo>> = photoRepository.getVisiblePhotos().asLiveData()

    // Position courante dans le ViewPager
    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    val currentPhoto = MediatorLiveData<Photo?>().apply {
        addSource(allPhotos) { photos ->
            value = currentPosition.value?.let { pos -> photos.getOrNull(pos) }
        }
        addSource(currentPosition) { pos ->
            value = allPhotos.value?.getOrNull(pos)
        }
    }

    fun setPosition(position: Int) {
        _currentPosition.value = position
    }
}