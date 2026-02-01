package com.cevague.vindex.util

import android.content.Context
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_BURST
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_DOCUMENT
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_OTHER
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_PANORAMA
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_PHOTO
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_SCREENSHOT
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_SELFIE
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_SOCIAL
import com.cevague.vindex.data.database.entity.Photo.Companion.MEDIA_TYPE_SQUARE
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaTypeFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDisplayName(mediaType: Int): String {
        val resId = when (mediaType) {
            MEDIA_TYPE_OTHER -> R.string.media_type_other
            MEDIA_TYPE_PHOTO -> R.string.media_type_photo
            MEDIA_TYPE_SELFIE -> R.string.media_type_selfie
            MEDIA_TYPE_PANORAMA -> R.string.media_type_panorama
            MEDIA_TYPE_BURST -> R.string.media_type_burst
            MEDIA_TYPE_SCREENSHOT -> R.string.media_type_screenshot
            MEDIA_TYPE_DOCUMENT -> R.string.media_type_document
            MEDIA_TYPE_SOCIAL -> R.string.media_type_social
            MEDIA_TYPE_SQUARE -> R.string.media_type_square
            else -> R.string.media_type_not_define
        }
        return context.getString(resId)
    }
}