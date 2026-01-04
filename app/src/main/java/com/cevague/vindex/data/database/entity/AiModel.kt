package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores configuration for imported AI models.
 */
@Entity(
    tableName = "ai_models",
    indices = [
        Index(value = ["model_type", "model_name"], unique = true)
    ]
)
data class AiModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_type")
    val modelType: String,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "model_path")
    val modelPath: String? = null,

    @ColumnInfo(name = "model_size")
    val modelSize: Long? = null,

    @ColumnInfo(name = "is_active", defaultValue = "0")
    val isActive: Boolean = false,

    @ColumnInfo(name = "is_builtin", defaultValue = "0")
    val isBuiltin: Boolean = false,

    @ColumnInfo(name = "added_at")
    val addedAt: Long
) {
    companion object {
        const val TYPE_CAPTIONING = "captioning"
        const val TYPE_EMBEDDING = "embedding"
        const val TYPE_TAGGING = "tagging"
        const val TYPE_FACE_DETECTION = "face_detection"
        const val TYPE_FACE_EMBEDDING = "face_embedding"
        const val TYPE_OCR = "ocr"
    }
}
