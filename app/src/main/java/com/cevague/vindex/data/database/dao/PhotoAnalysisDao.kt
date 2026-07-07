package com.cevague.vindex.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import kotlinx.coroutines.flow.Flow

/** Projection légère du scan vectoriel : jamais l'entité complète en liste. */
data class EmbeddingRow(
    @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "embedding") val embedding: ByteArray,
    @ColumnInfo(name = "embedding_dim") val embeddingDim: Int
)

@Dao
interface PhotoAnalysisDao {

    /** Embeddings d'un sous-ensemble de photos (candidats filtrés) ; à chunker à 900. */
    @Query(
        """
        SELECT photo_id, embedding, embedding_dim FROM photo_analyses
        WHERE analysis_type = :type AND model_name = :modelName
          AND embedding IS NOT NULL AND photo_id IN (:photoIds)
    """
    )
    suspend fun getEmbeddingsForPhotos(
        type: String,
        modelName: String,
        photoIds: List<Long>
    ): List<EmbeddingRow>

    /** Scan complet paginé par photo_id croissant (mémoire bornée, phase 2 §4.7). */
    @Query(
        """
        SELECT photo_id, embedding, embedding_dim FROM photo_analyses
        WHERE analysis_type = :type AND model_name = :modelName
          AND embedding IS NOT NULL AND photo_id > :afterPhotoId
        ORDER BY photo_id
        LIMIT :limit
    """
    )
    suspend fun getEmbeddingsChunk(
        type: String,
        modelName: String,
        afterPhotoId: Long,
        limit: Int
    ): List<EmbeddingRow>

    @Query("SELECT * FROM photo_analyses WHERE photo_id = :photoId")
    fun getAnalysesForPhoto(photoId: Long): Flow<List<PhotoAnalysis>>

    @Query("SELECT * FROM photo_analyses WHERE photo_id = :photoId AND analysis_type = :type AND model_name = :modelName LIMIT 1")
    suspend fun getAnalysis(photoId: Long, type: String, modelName: String): PhotoAnalysis?

    @Query("SELECT * FROM photo_analyses WHERE analysis_type = :type AND model_name = :modelName")
    fun getAnalysesByTypeAndModel(type: String, modelName: String): Flow<List<PhotoAnalysis>>

    @Upsert
    suspend fun upsertAnalysis(analysis: PhotoAnalysis)

    @Upsert
    suspend fun upsertAnalyses(analyses: List<PhotoAnalysis>)

    @Query("DELETE FROM photo_analyses WHERE photo_id = :photoId")
    suspend fun deleteAnalysesForPhoto(photoId: Long)

    @Query("DELETE FROM photo_analyses WHERE analysis_type = :type")
    suspend fun deleteAnalysesByType(type: String)

    @Query("DELETE FROM photo_analyses")
    suspend fun deleteAllAnalyses()
}
