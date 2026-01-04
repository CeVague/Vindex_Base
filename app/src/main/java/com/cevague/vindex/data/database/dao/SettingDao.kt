package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cevague.vindex.data.database.entity.Setting
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<Setting>>

    @Query("SELECT * FROM settings WHERE key = :key")
    fun getSetting(key: String): Flow<Setting?>

    @Query("SELECT value FROM settings WHERE key = :key")
    fun getValue(key: String): Flow<String?>

    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSettingOnce(key: String): Setting?

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getValueOnce(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: Setting)

    suspend fun setValue(key: String, value: String) {
        set(Setting(key = key, value = value, updatedAt = System.currentTimeMillis()))
    }

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
