package com.cevague.vindex.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cevague.vindex.data.database.entity.Face
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {

    data class FaceWithPhoto(
        val id: Long,
        val filePath: String,
        val boxLeft: Float,
        val boxTop: Float,
        val boxRight: Float,
        val boxBottom: Float
    )

    // Queries - reactive

    @Query("SELECT COUNT(*) FROM faces WHERE assignment_type = 'pending'")
    fun getPendingFaceCount(): Flow<Int>

    @Query("SELECT * FROM faces WHERE id = :id")
    fun getFaceById(id: Long): Flow<Face?>

    // Queries - one-shot

    @Query("SELECT * FROM faces WHERE person_id = :personId")
    suspend fun getFacesByPersonOnce(personId: Long): List<Face>

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun getFaceByIdOnce(id: Long): Face?

    @Query("SELECT * FROM faces WHERE person_id IS NOT NULL AND embedding IS NOT NULL")
    suspend fun getAllIdentifiedFacesWithEmbedding(): List<Face>

    // Réservé au futur « max-sur-les-visages » (cf. backlog), comme
    // getAllIdentifiedFacesWithEmbedding.
    @Query("SELECT * FROM faces WHERE person_id = :personId AND embedding IS NOT NULL")
    suspend fun getFacesWithEmbeddingByPerson(personId: Long): List<Face>

    @Query(
        """
    SELECT f.id as id, ph.file_path as filePath, f.box_left as boxLeft, f.box_top as boxTop, 
           f.box_right as boxRight, f.box_bottom as boxBottom
    FROM faces f
    JOIN photos ph ON f.photo_id = ph.id
    WHERE f.person_id = :personId
    ORDER BY f.is_primary DESC, f.confidence DESC
    LIMIT 1
"""
    )
    suspend fun getPrimaryFaceWithPhoto(personId: Long): FaceWithPhoto?

    @Query(
        """
    SELECT f.id, p.file_path AS filePath, f.box_left AS boxLeft, 
           f.box_top AS boxTop, f.box_right AS boxRight, f.box_bottom AS boxBottom
    FROM faces f 
    INNER JOIN photos p ON f.photo_id = p.id 
    WHERE f.assignment_type = 'pending' AND f.id NOT IN (:excludeIds)
    LIMIT 1
"""
    )
    suspend fun getNextPendingFaceExcluding(excludeIds: Set<Long>): FaceWithPhoto?

    // Pour marquer comme "ignored" (Face.ASSIGNMENT_IGNORED — @Query n'accepte pas de const).
    // `person_id` est remis à NULL comme dans la variante groupe : un visage `pending`
    // porte la personne *suggérée*, et l'y laisser après « rien à voir » ferait compter
    // sa photo chez elle (fiche, viewer, recherche, exports de calibration).
    @Query(
        """
        UPDATE faces SET
            assignment_type = 'ignored',
            exclusion_reason = :reason,
            person_id = NULL,
            assignment_confidence = NULL,
            assigned_at = :timestamp
        WHERE id = :id
    """
    )
    suspend fun markAsIgnored(
        id: Long,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Écarte d'un coup tous les visages d'un groupe, avec la raison ([reason] =
     * `Face.EXCLUDED_*`).
     *
     * `person_id` est remis à NULL ici même : le groupe est supprimé juste après, et
     * les visages ne doivent appartenir à personne — pas plus qu'ils ne doivent
     * retomber dans la file d'identification, d'où `ignored` plutôt que `pending`.
     */
    @Query(
        """
        UPDATE faces SET
            assignment_type = 'ignored',
            exclusion_reason = :reason,
            person_id = NULL,
            assignment_confidence = NULL,
            assigned_at = :timestamp
        WHERE person_id = :personId
    """
    )
    suspend fun markAllAsIgnoredForPerson(
        personId: Long,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Visages identifiés, hors personnes **masquées** : le jeu de mesure.
     *
     * Les masquées en sont exclues parce qu'un inconnu n'est jamais regroupé
     * sérieusement — deux visages du même passant peuvent finir dans deux groupes
     * masqués distincts, ce qui inscrirait « personnes différentes » dans la vérité
     * terrain alors que c'est la même. Mieux vaut ne pas les compter que compter faux.
     */
    @Query(
        """
        SELECT f.* FROM faces f
        JOIN persons p ON p.id = f.person_id
        WHERE f.embedding IS NOT NULL AND p.is_hidden = 0
    """
    )
    suspend fun getFacesForCalibration(): List<Face>

    /**
     * Visage étiqueté (hors masqués) + sa photo : pour ré-embarquer avec un autre
     * modèle et mesurer sa qualité. [photoWidth]/[photoHeight] sont les dimensions
     * **d'origine** — seules elles disent combien de pixels réels le visage occupe.
     */
    data class LabeledFace(
        val id: Long,
        @ColumnInfo(name = "person_id") val personId: Long,
        val filePath: String,
        @ColumnInfo(name = "box_left") val boxLeft: Float,
        @ColumnInfo(name = "box_top") val boxTop: Float,
        @ColumnInfo(name = "box_right") val boxRight: Float,
        @ColumnInfo(name = "box_bottom") val boxBottom: Float,
        val confidence: Float?,
        @ColumnInfo(name = "exclusion_reason") val exclusionReason: String?,
        val photoWidth: Int?,
        val photoHeight: Int?
    )

    @Query(
        """
        SELECT f.id, f.person_id, ph.file_path AS filePath,
               f.box_left, f.box_top, f.box_right, f.box_bottom,
               f.confidence, f.exclusion_reason,
               ph.width AS photoWidth, ph.height AS photoHeight
        FROM faces f
        JOIN persons p ON p.id = f.person_id
        JOIN photos ph ON ph.id = f.photo_id
        WHERE p.is_hidden = 0
    """
    )
    suspend fun getLabeledFacesWithPhoto(): List<LabeledFace>

    // Insert

    @Insert
    suspend fun insertAll(faces: List<Face>): List<Long>

    // Update

    @Query(
        """
        UPDATE faces SET 
            person_id = :personId, 
            assignment_type = :assignmentType, 
            assignment_confidence = :confidence,
            weight = :weight,
            assigned_at = :timestamp 
        WHERE id = :id
    """
    )
    suspend fun assignToPerson(
        id: Long,
        personId: Long?,
        assignmentType: String,
        confidence: Float?,
        weight: Float,
        timestamp: Long
    )

    @Query("UPDATE faces SET person_id = NULL, assignment_type = 'pending', assignment_confidence = NULL WHERE person_id = :personId")
    suspend fun unassignFromPerson(personId: Long)

    @Query("UPDATE faces SET person_id = :newPersonId WHERE person_id = :oldPersonId")
    suspend fun reassignAllFaces(oldPersonId: Long, newPersonId: Long)

    @Query("UPDATE faces SET is_primary = :isPrimary WHERE id = :id")
    suspend fun setPrimary(id: Long, isPrimary: Boolean)

    // Delete

    @Query("DELETE FROM faces")
    suspend fun deleteAll()

    @Query("DELETE FROM faces WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
