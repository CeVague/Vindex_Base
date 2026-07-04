package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "photo_analyses",
    primaryKeys = ["photo_id", "analysis_type", "model_name"],
    indices = [
        Index(value = ["analysis_type", "model_name"], name = "idx_analyses_type_model")
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
    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "analysis_type")
    val analysisType: String,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "text_result")
    val textResult: String? = null,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "embedding_dim")
    val embeddingDim: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null
) {
    companion object {
        const val TYPE_CAPTION = "caption"
        const val TYPE_CAPTION_EMBEDDING = "caption_embedding"
        const val TYPE_CLIP_EMBEDDING = "clip_embedding"
        const val TYPE_TAGS = "tags"
        const val TYPE_OCR = "ocr"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PhotoAnalysis

        return photoId == other.photoId &&
                analysisType == other.analysisType &&
                modelName == other.modelName
    }

    override fun hashCode(): Int {
        var result = photoId.hashCode()
        result = 31 * result + analysisType.hashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}
