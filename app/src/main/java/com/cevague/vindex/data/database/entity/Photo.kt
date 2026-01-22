package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["date_taken"]),
        Index(value = ["folder_path"]),
        Index(value = ["file_path"], unique = true),
        Index(value = ["is_metadata_extracted"]),
        Index(value = ["relative_path"])
    ]
)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_last_modified")
    val fileLastModified: Long,

    @ColumnInfo(name = "folder_path")
    val folderPath: String,

    @ColumnInfo(name = "relative_path")
    val relativePath: String? = null,

    @ColumnInfo(name = "date_taken")
    val dateTaken: Long? = null,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long,

    val width: Int? = null,
    val height: Int? = null,
    val orientation: Int? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    @ColumnInfo(name = "location_name")
    val locationName: String? = null,

    @ColumnInfo(name = "camera_make")
    val cameraMake: String? = null,

    @ColumnInfo(name = "camera_model")
    val cameraModel: String? = null,

    @ColumnInfo(name = "file_size")
    val fileSize: Long? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "media_type", defaultValue = "'photo'")
    val mediaType: String = "photo",

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,

    @ColumnInfo(name = "is_metadata_extracted", defaultValue = "0")
    val isMetadataExtracted: Boolean = false,

    @ColumnInfo(name = "quality_score")
    val qualityScore: Float? = null,

    @ColumnInfo(name = "is_blurry", defaultValue = "0")
    val isBlurry: Boolean = false,

    val description: String? = null,

    @ColumnInfo(name = "description_embedding", typeAffinity = ColumnInfo.BLOB)
    val descriptionEmbedding: ByteArray? = null,

    @ColumnInfo(name = "description_model")
    val descriptionModel: String? = null,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String? = null,

    @ColumnInfo(name = "tags_model")
    val tagsModel: String? = null,

    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,

    @ColumnInfo(name = "ocr_model")
    val ocrModel: String? = null,

    @ColumnInfo(name = "last_analyzed")
    val lastAnalyzed: Long? = null,

    @ColumnInfo(name = "needs_reanalysis", defaultValue = "1")
    val needsReanalysis: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Photo
        return id == other.id && filePath == other.filePath
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }
}
