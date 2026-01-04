package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a person identified through face recognition.
 * Contains the centroid embedding for face clustering.
 */
@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "photo_count", defaultValue = "0")
    val photoCount: Int = 0,

    @ColumnInfo(name = "centroid_embedding", typeAffinity = ColumnInfo.BLOB)
    val centroidEmbedding: ByteArray? = null,

    @ColumnInfo(name = "centroid_updated_at")
    val centroidUpdatedAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Person

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
