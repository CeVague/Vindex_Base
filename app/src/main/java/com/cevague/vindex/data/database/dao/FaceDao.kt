package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cevague.vindex.data.database.entity.Face
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {

    // Queries - reactive

    @Query("SELECT * FROM faces WHERE photo_id = :photoId")
    fun getFacesByPhoto(photoId: Long): Flow<List<Face>>

    @Query("SELECT * FROM faces WHERE person_id = :personId")
    fun getFacesByPerson(personId: Long): Flow<List<Face>>

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

    @Query("SELECT COUNT(*) FROM faces WHERE photo_id = :photoId")
    suspend fun getFaceCountForPhoto(photoId: Long): Int

    // Insert

    @Insert
    suspend fun insert(face: Face): Long

    @Insert
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

    @Query("DELETE FROM faces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM faces WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
