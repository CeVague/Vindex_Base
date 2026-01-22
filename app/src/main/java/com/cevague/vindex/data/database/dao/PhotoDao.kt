package com.cevague.vindex.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cevague.vindex.data.database.entity.Photo
import kotlinx.coroutines.flow.Flow

data class FilePathAndSize(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "file_last_modified") val fileLastModified: Long
)

data class PhotoSummary(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean
)

@Dao
interface PhotoDao {

    // Queries - reactive (Flow updates automatically when data changes)

    @Query("SELECT * FROM photos ORDER BY date_taken DESC")
    fun getAllPhotos(): Flow<List<Photo>>


    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos ORDER BY date_taken DESC")
    fun getAllPhotosSummary(): Flow<List<PhotoSummary>>

    @Query("SELECT id, file_path, file_size, file_last_modified FROM photos")
    fun getAllPathsAndSizes(): Flow<List<FilePathAndSize>>

    @Query("SELECT * FROM photos WHERE is_hidden = 0 ORDER BY date_taken DESC")
    fun getVisiblePhotos(): Flow<List<Photo>>

    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos WHERE is_hidden = 0 ORDER BY date_taken DESC")
    fun getVisiblePhotosSummary(): Flow<List<PhotoSummary>>

    @Query("SELECT * FROM photos WHERE folder_path = :folderPath ORDER BY date_taken DESC")
    fun getPhotosByFolder(folderPath: String): Flow<List<Photo>>

    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos WHERE folder_path = :folderPath ORDER BY date_taken DESC")
    fun getPhotosSummaryByFolder(folderPath: String): Flow<List<PhotoSummary>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE file_path = :filePath LIMIT 1")
    fun getPhotoByPath(filePath: String): Flow<Photo?>

    @Query("SELECT COUNT(*) FROM photos")
    fun getPhotoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE is_hidden = 0")
    fun getVisiblePhotoCount(): Flow<Int>

    @Query("SELECT DISTINCT folder_path FROM photos ORDER BY folder_path")
    fun getAllFolders(): Flow<List<String>>

    @Query(
        """
        SELECT * FROM photos 
        WHERE file_name LIKE '%' || :query || '%' 
           OR file_path LIKE '%' || :query || '%'
        ORDER BY date_taken DESC
    """
    )
    fun searchByFileName(query: String): Flow<List<Photo>>

    @Query(
        """
        SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos 
        WHERE file_name LIKE '%' || :query || '%' 
           OR file_path LIKE '%' || :query || '%'
        ORDER BY date_taken DESC
    """
    )
    fun searchByFileNameSummary(query: String): Flow<List<PhotoSummary>>

    @Query("SELECT * FROM photos WHERE needs_reanalysis = 1")
    fun getPhotosNeedingAnalysis(): Flow<List<Photo>>

    // Queries - one-shot (suspend for single operations)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoByIdOnce(id: Long): Photo?

    @Query("SELECT * FROM photos WHERE file_path = :filePath LIMIT 1")
    suspend fun getPhotoByPathOnce(filePath: String): Photo?

    @Query("SELECT EXISTS(SELECT 1 FROM photos WHERE file_path = :filePath)")
    suspend fun existsByPath(filePath: String): Boolean

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getPhotosByIds(ids: List<Long>): List<Photo>

    @Query("SELECT * FROM photos WHERE is_metadata_extracted = 0")
    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo>


    @Query("SELECT MAX(file_last_modified) FROM photos")
    suspend fun getLastSyncTimestamp(): Long?

    // Insert

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: Photo): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<Photo>): List<Long>

    // Update

    @Update
    suspend fun update(photo: Photo)

    @Query("UPDATE photos SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE photos SET is_hidden = :isHidden WHERE id = :id")
    suspend fun setHidden(id: Long, isHidden: Boolean)

    @Query("UPDATE photos SET description = :description, description_model = :model, last_analyzed = :timestamp WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String?, model: String?, timestamp: Long)

    @Query("UPDATE photos SET description_embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: ByteArray?)

    @Query("UPDATE photos SET tags_json = :tagsJson, tags_model = :model WHERE id = :id")
    suspend fun updateTags(id: Long, tagsJson: String?, model: String?)

    @Query("UPDATE photos SET ocr_text = :ocrText, ocr_model = :model WHERE id = :id")
    suspend fun updateOcr(id: Long, ocrText: String?, model: String?)

    @Query("UPDATE photos SET needs_reanalysis = :needsReanalysis WHERE id = :id")
    suspend fun setNeedsReanalysis(id: Long, needsReanalysis: Boolean)

    @Query("UPDATE photos SET needs_reanalysis = 1")
    suspend fun markAllForReanalysis()

    // Delete

    @Delete
    suspend fun delete(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM photos WHERE file_path = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM photos WHERE file_path IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>)

    @Query("DELETE FROM photos WHERE folder_path = :folderPath")
    suspend fun deleteByFolder(folderPath: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
