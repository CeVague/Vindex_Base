package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Logs analysis operations for debugging and resumption after interruption.
 */
@Entity(
    tableName = "analysis_log",
    foreignKeys = [
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
data class AnalysisLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "analysis_type")
    val analysisType: String,

    @ColumnInfo(name = "model_used")
    val modelUsed: String? = null,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    val success: Boolean? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    companion object {
        const val TYPE_SCAN = "scan"
        const val TYPE_CAPTIONING = "captioning"
        const val TYPE_EMBEDDING = "embedding"
        const val TYPE_TAGGING = "tagging"
        const val TYPE_FACE = "face"
        const val TYPE_OCR = "ocr"
    }
}
