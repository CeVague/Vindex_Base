package com.cevague.vindex.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    // Face sans ByteArray (trop lourd)
    data class FaceSummary(
        @ColumnInfo(name = "id") val id: Long,
        @ColumnInfo(name = "photo_id") val photoId: Long,
        @ColumnInfo(name = "box_left") val boxLeft: Float,
        @ColumnInfo(name = "box_top") val boxTop: Float,
        @ColumnInfo(name = "box_right") val boxRight: Float,
        @ColumnInfo(name = "box_bottom") val boxBottom: Float,
        @ColumnInfo(name = "person_id") val personId: Long?
    )

    // Queries - reactive

    @Query("SELECT * FROM faces WHERE photo_id = :photoId")
    fun getFacesByPhoto(photoId: Long): Flow<List<Face>>

    @Query("SELECT id, photo_id, box_left, box_top, box_right, box_bottom, person_id FROM faces WHERE photo_id = :photoId")
    fun getFaceSummariesByPhoto(photoId: Long): Flow<List<FaceSummary>>

    @Query("SELECT * FROM faces WHERE person_id = :personId")
    fun getFacesByPerson(personId: Long): Flow<List<Face>>

    @Query("SELECT id, photo_id, box_left, box_top, box_right, box_bottom, person_id FROM faces WHERE person_id = :personId")
    fun getFacesSummaryByPerson(personId: Long): Flow<List<FaceSummary>>

    @Query("SELECT * FROM faces WHERE person_id IS NULL")
    fun getUnidentifiedFaces(): Flow<List<Face>>

    @Query("SELECT * FROM faces WHERE assignment_type = 'pending'")
    fun getPendingFaces(): Flow<List<Face>>

    @Query("SELECT COUNT(*) FROM faces WHERE assignment_type = 'pending'")
    fun getPendingFaceCount(): Flow<Int>

    @Query("SELECT * FROM faces WHERE id = :id")
    fun getFaceById(id: Long): Flow<Face?>

    // Queries - one-shot

    @Query("SELECT * FROM faces WHERE photo_id = :photoId")
    suspend fun getFacesByPhotoOnce(photoId: Long): List<Face>

    @Query("SELECT * FROM faces WHERE person_id = :personId")
    suspend fun getFacesByPersonOnce(personId: Long): List<Face>

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun getFaceByIdOnce(id: Long): Face?

    @Query("SELECT * FROM faces WHERE person_id IS NOT NULL AND embedding IS NOT NULL")
    suspend fun getAllIdentifiedFacesWithEmbedding(): List<Face>

    @Query("SELECT * FROM faces WHERE person_id = :personId AND embedding IS NOT NULL")
    suspend fun getFacesWithEmbeddingByPerson(personId: Long): List<Face>

    @Query("SELECT * FROM faces WHERE is_primary = 1 AND person_id = :personId LIMIT 1")
    suspend fun getPrimaryFaceForPerson(personId: Long): Face?

    @Query(
        """
        SELECT ph.file_path FROM faces f
        JOIN photos ph ON f.photo_id = ph.id
        WHERE f.person_id = :personId
        ORDER BY f.is_primary DESC, f.confidence DESC
        LIMIT 1
    """
    )
    suspend fun getCoverPhotoPathForPerson(personId: Long): String?

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

    @Query("SELECT COUNT(*) FROM faces WHERE photo_id = :photoId")
    suspend fun getFaceCountForPhoto(photoId: Long): Int

    @Query(
        """
    SELECT f.id as id, ph.file_path as filePath, f.box_left as boxLeft, 
           f.box_top as boxTop, f.box_right as boxRight, f.box_bottom as boxBottom
    FROM faces f
    JOIN photos ph ON f.photo_id = ph.id
    WHERE f.assignment_type = 'pending'
"""
    )
    suspend fun getPendingFaceWithPhoto(): List<FaceWithPhoto>

    @Query(
        """
    SELECT f.id as id, ph.file_path as filePath, f.box_left as boxLeft, 
           f.box_top as boxTop, f.box_right as boxRight, f.box_bottom as boxBottom
    FROM faces f
    JOIN photos ph ON f.photo_id = ph.id
    WHERE f.assignment_type = 'pending'
    LIMIT 1
"""
    )
    suspend fun getNextPendingFaceWithPhoto(): FaceWithPhoto?


    @Query("""
    SELECT f.id, p.file_path AS filePath, f.box_left AS boxLeft, 
           f.box_top AS boxTop, f.box_right AS boxRight, f.box_bottom AS boxBottom
    FROM faces f 
    INNER JOIN photos p ON f.photo_id = p.id 
    WHERE f.assignment_type = 'pending' AND f.id NOT IN (:excludeIds)
    LIMIT 1
""")
    suspend fun getNextPendingFaceExcluding(excludeIds: Set<Long>): FaceWithPhoto?

    // Pour marquer comme "ignored"
    @Query("UPDATE faces SET assignment_type = 'ignored', assigned_at = :timestamp WHERE id = :id")
    suspend fun markAsIgnored(id: Long, timestamp: Long = System.currentTimeMillis())

    // Insert

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: Face): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(faces: List<Face>): List<Long>

    // Update

    @Update
    suspend fun update(face: Face)

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

    @Query("UPDATE faces SET embedding = :embedding, embedding_model = :model WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: ByteArray?, model: String?)

    // Delete

    @Delete
    suspend fun delete(face: Face)

    @Query("DELETE FROM faces")
    suspend fun deleteAll()

    @Query("DELETE FROM faces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM faces WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
