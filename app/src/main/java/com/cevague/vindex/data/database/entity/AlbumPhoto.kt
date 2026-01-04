package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for many-to-many relationship between albums and photos.
 */
@Entity(
    tableName = "album_photos",
    primaryKeys = ["album_id", "photo_id"],
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["photo_id"])
    ]
)
data class AlbumPhoto(
    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
