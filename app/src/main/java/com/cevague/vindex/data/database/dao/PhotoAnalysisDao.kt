package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoAnalysisDao {

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

    @Query("DELETE FROM photo_analyses")
    suspend fun deleteAllAnalyses()
}
