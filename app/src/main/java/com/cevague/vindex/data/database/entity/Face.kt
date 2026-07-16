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

    /**
     * Pourquoi le visage a été écarté, quand [assignmentType] vaut `ignored` — et
     * `null` sinon : c'est une union discriminée, la raison n'existe pas sans
     * l'exclusion.
     *
     * L'effet est le même dans les deux cas (le visage sort du jeu), mais pas la
     * signification, et elle sert dès maintenant : pour calibrer le `score_threshold`
     * du détecteur, une **affiche à 0,9 n'est pas une erreur** alors qu'un chat à 0,65
     * en est une. Les confondre reviendrait à mesurer deux populations pour une.
     */
    @ColumnInfo(name = "exclusion_reason")
    val exclusionReason: String? = null,

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

    companion object {
        /** Assigné par le clustering, au-dessus du seuil haut. */
        const val ASSIGNMENT_AUTO = "auto"

        /** Suggestion en attente de confirmation : ne compte pas dans le centroïde. */
        const val ASSIGNMENT_PENDING = "pending"

        /** Confirmé par l'utilisateur : seule vérité terrain, jamais réécrit par l'automate. */
        const val ASSIGNMENT_MANUAL = "manual"

        /**
         * Écarté par l'utilisateur : sort définitivement de la file d'identification
         * et des centroïdes, sans effacer la détection. La raison vit dans
         * [exclusionReason].
         *
         * Room n'accepte que des littéraux dans `@Query` : la valeur est donc répétée
         * en dur dans les requêtes de FaceDao — c'est ici qu'elle est déclarée.
         */
        const val ASSIGNMENT_IGNORED = "ignored"

        /** Ce n'est pas un visage humain : animal, statue. L'embedding est du bruit. */
        const val EXCLUDED_NOT_A_PERSON = "not_a_person"

        /**
         * Une personne, mais **représentée** : dessin, affiche, écran, photo de photo.
         *
         * ⚠ **Mesuré le 2026-07-16, contre l'intuition** : un dessin grossier ne vole
         * l'identité de personne — **0 paire sur 301** entre un dessin et une vraie
         * personne atteint le seuil de fusion (max 0,315). C'est du **bruit**, comme un
         * animal, pas un aimant.
         *
         * En revanche, **aucune mesure de qualité ne l'attrape** (0/9 rejetés) : un
         * dessin est frontal, net, bien détecté — son résidu d'alignement est *meilleur*
         * que celui d'un vrai visage, il est géométriquement **plus parfait**. C'est un
         * problème sémantique, pas de qualité : cette catégorie restera manuelle.
         *
         * (Une affiche **photoréaliste** n'a pas été mesurée et reste à surveiller :
         * elle, pourrait ressembler à la vraie personne.)
         */
        const val EXCLUDED_DEPICTION = "depiction"

        /**
         * Écarté **automatiquement** : détection trop mauvaise pour valoir une
         * identité (cf. `faceQuality`). Reflets, mains, silhouettes floues d'arrière-plan.
         *
         * La ligne est **conservée**, pas supprimée : la décision est ainsi auditable
         * (l'export de calibration la voit), réversible, et un seuil mal réglé se
         * constate au lieu de se deviner.
         */
        const val EXCLUDED_LOW_QUALITY = "low_quality"
    }
}
