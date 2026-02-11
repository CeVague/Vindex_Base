package com.cevague.vindex.ui.viewer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
sealed class ViewerSource : Parcelable {
    data class Gallery(val startPhotoId: Long) : ViewerSource()
    data class Search(val photoIds: List<Long>, val startPhotoId: Long) : ViewerSource()
    data class Album(val albumId: Long, val startPhotoId: Long) : ViewerSource()
}