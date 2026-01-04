package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cevague.vindex.data.database.entity.AnalysisLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisLogDao {

    @Query("SELECT * FROM analysis_log WHERE photo_id = :photoId ORDER BY started_at DESC")
    fun getLogsForPhoto(photoId: Long): Flow<List<AnalysisLog>>

    @Query("SELECT * FROM analysis_log WHERE photo_id = :photoId AND analysis_type = :type ORDER BY started_at DESC LIMIT 1")
    suspend fun getLastLogForPhotoAndType(photoId: Long, type: String): AnalysisLog?

    @Query("SELECT * FROM analysis_log WHERE success = 0 ORDER BY started_at DESC")
    fun getFailedLogs(): Flow<List<AnalysisLog>>

    @Query("SELECT * FROM analysis_log WHERE completed_at IS NULL ORDER BY started_at DESC")
    suspend fun getIncompleteLogs(): List<AnalysisLog>

    @Insert
    suspend fun insert(log: AnalysisLog): Long

    @Query("UPDATE analysis_log SET completed_at = :completedAt, success = :success, error_message = :errorMessage WHERE id = :id")
    suspend fun complete(id: Long, completedAt: Long, success: Boolean, errorMessage: String?)

    @Query("DELETE FROM analysis_log WHERE photo_id = :photoId")
    suspend fun deleteByPhotoId(photoId: Long)

    @Query("DELETE FROM analysis_log WHERE started_at < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)

    @Query("DELETE FROM analysis_log")
    suspend fun deleteAll()
}
