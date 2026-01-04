package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a detected face in a photo.
 * Links to Photo (required) and Person (optional, null if not identified).
 */
@Entity(
    tableName = "faces",
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["photo_id"]),
        Index(value = ["person_id"]),
        Index(value = ["assignment_type"])
    ]
)
data class Face(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "person_id")
    val personId: Long? = null,

    // Bounding box (normalized coordinates 0-1)
    @ColumnInfo(name = "box_left")
    val boxLeft: Float,

    @ColumnInfo(name = "box_top")
    val boxTop: Float,

    @ColumnInfo(name = "box_right")
    val boxRight: Float,

    @ColumnInfo(name = "box_bottom")
    val boxBottom: Float,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "embedding_model")
    val embeddingModel: String? = null,

    val confidence: Float? = null,

    @ColumnInfo(name = "is_primary", defaultValue = "0")
    val isPrimary: Boolean = false,

    @ColumnInfo(name = "assignment_type")
    val assignmentType: String? = null,

    @ColumnInfo(name = "assignment_confidence")
    val assignmentConfidence: Float? = null,

    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long? = null,

    @ColumnInfo(defaultValue = "1.0")
    val weight: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Face

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
