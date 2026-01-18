package com.cevague.vindex.data.database.entity

data class FaceWithPhoto(
    val faceId: Long,
    val filePath: String,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float
)