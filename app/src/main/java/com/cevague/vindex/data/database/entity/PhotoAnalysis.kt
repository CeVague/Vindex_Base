package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_analyses",
    indices = [
        Index(value = ["photo_id"]),
        Index(value = ["analysis_type"]),
        Index(value = ["model_name"]),
        Index(value = ["photo_id", "analysis_type", "model_name"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PhotoAnalysis(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "analysis_type")
    val analysisType: String,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "result_value")
    val resultValue: String? = null,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_CAPTION = "caption"
        const val TYPE_TAGS = "tags"
        const val TYPE_OCR = "ocr"
        const val TYPE_EMBEDDING = "embedding"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PhotoAnalysis

        if (id != other.id) return false
        if (photoId != other.photoId) return false
        if (analysisType != other.analysisType) return false
        if (modelName != other.modelName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + photoId.hashCode()
        result = 31 * result + analysisType.hashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}
