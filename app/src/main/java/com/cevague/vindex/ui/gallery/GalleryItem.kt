package com.cevague.vindex.ui.gallery

import com.cevague.vindex.data.database.dao.PhotoSummary

sealed class GalleryItem {

    data class Header(
        val title: String,
        val id: String
    ) : GalleryItem()

    data class PhotoItem(
        val photo: PhotoSummary
    ) : GalleryItem()
}