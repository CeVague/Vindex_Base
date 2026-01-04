package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an album (folder-based, manual, or auto-generated).
 */
@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["cover_photo_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["cover_photo_id"])
    ]
)
data class Album(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "album_type")
    val albumType: String,

    @ColumnInfo(name = "folder_path")
    val folderPath: String? = null,

    @ColumnInfo(name = "cover_photo_id")
    val coverPhotoId: Long? = null,

    @ColumnInfo(name = "photo_count", defaultValue = "0")
    val photoCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "auto_criteria_json")
    val autoCriteriaJson: String? = null
) {
    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_MANUAL = "manual"
        const val TYPE_AUTO_EVENT = "auto_event"
        const val TYPE_AUTO_LOCATION = "auto_location"
        const val TYPE_AUTO_PERSON = "auto_person"
    }
}
