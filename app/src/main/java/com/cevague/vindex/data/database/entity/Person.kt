package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "persons",
    indices = [Index(value = ["name"], unique = true)]
)

data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "photo_count", defaultValue = "0")
    val photoCount: Int = 0,

    /**
     * Quelqu'un d'identifié mais sans intérêt : un passant, une silhouette d'arrière-plan.
     *
     * Propriété de l'**identité**, pas des visages — ils sont bons, le regroupement est
     * bon, c'est l'intérêt qui est nul. D'où un drapeau ici et non une exclusion sur
     * `faces`.
     *
     * ⚠ Le groupe reste **vivant** : le clustering continue de lui assigner ses
     * nouveaux visages. C'est contre-intuitif, mais c'est ce qui débarrasse de lui —
     * le supprimer relâcherait ses visages, qui reformeraient un groupe visible à
     * chaque analyse, et de plus en plus fragmenté.
     */
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,

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
