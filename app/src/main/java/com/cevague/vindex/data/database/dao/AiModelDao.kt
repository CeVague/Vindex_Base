package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cevague.vindex.data.database.entity.AiModel
import kotlinx.coroutines.flow.Flow

@Dao
interface AiModelDao {

    @Query("SELECT * FROM ai_models ORDER BY model_type, model_name")
    fun getAllModels(): Flow<List<AiModel>>

    @Query("SELECT * FROM ai_models WHERE model_type = :type ORDER BY model_name")
    fun getModelsByType(type: String): Flow<List<AiModel>>

    @Query("SELECT * FROM ai_models WHERE model_type = :type AND is_active = 1 LIMIT 1")
    fun getActiveModel(type: String): Flow<AiModel?>

    @Query("SELECT * FROM ai_models WHERE id = :id")
    suspend fun getModelById(id: Long): AiModel?

    @Query("SELECT * FROM ai_models WHERE model_type = :type AND is_active = 1 LIMIT 1")
    suspend fun getActiveModelOnce(type: String): AiModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: AiModel): Long

    @Update
    suspend fun update(model: AiModel)

    @Query("UPDATE ai_models SET is_active = 0 WHERE model_type = :type")
    suspend fun deactivateAllOfType(type: String)

    @Query("UPDATE ai_models SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Delete
    suspend fun delete(model: AiModel)

    @Query("DELETE FROM ai_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}
