package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Stores various hashes for duplicate detection.
 */
@Entity(
    tableName = "photo_hashes",
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PhotoHash(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    val phash: String? = null,

    val dhash: String? = null,

    @ColumnInfo(name = "file_hash")
    val fileHash: String? = null
)
