package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cevague.vindex.data.database.entity.PhotoHash

@Dao
interface PhotoHashDao {

    @Query("SELECT * FROM photo_hashes WHERE photo_id = :photoId")
    suspend fun getHashesForPhoto(photoId: Long): PhotoHash?

    @Query("SELECT * FROM photo_hashes WHERE phash = :phash")
    suspend fun findByPhash(phash: String): List<PhotoHash>

    @Query("SELECT * FROM photo_hashes WHERE file_hash = :fileHash")
    suspend fun findByFileHash(fileHash: String): List<PhotoHash>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hash: PhotoHash)

    @Query("DELETE FROM photo_hashes WHERE photo_id = :photoId")
    suspend fun deleteByPhotoId(photoId: Long)
}
