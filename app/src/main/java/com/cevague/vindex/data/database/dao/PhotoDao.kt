package com.cevague.vindex.data.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
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

fun Photo.toSummary(): PhotoSummary {
    return PhotoSummary(id = this.id,
        filePath = this.filePath,
        fileName = this.fileName,
        dateAdded = this.dateAdded,
        dateTaken = this.dateTaken,
        isFavorite = this.isFavorite
    )
}

fun List<Photo>.toSummaryList(): List<PhotoSummary> {
    return this.map { it.toSummary() }
}

@Dao
interface PhotoDao {

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
    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos WHERE is_hidden = 0 ORDER BY date_taken DESC")
    fun getVisiblePhotosSummaryPaged(): PagingSource<Int, PhotoSummary>
    @Query("SELECT * FROM photos WHERE relative_path = :relativePath ORDER BY date_taken DESC")
    fun getPhotosByRelativePath(relativePath: String): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<Photo?>

    @Query("SELECT id, file_path, date_taken, file_name, date_added, is_favorite FROM photos WHERE id IN (:ids)")
    fun getPhotosSummaryByIds(ids: List<Long>): Flow<List<PhotoSummary>>

    @Query("SELECT COUNT(*) FROM photos")
    fun getPhotoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE is_hidden = 0")
    fun getVisiblePhotoCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM photos")
    suspend fun getTotalStorageUsed(): Long

    @Query("SELECT DISTINCT relative_path FROM photos WHERE relative_path IS NOT NULL ORDER BY relative_path")
    fun getAllFolders(): Flow<List<String>>

    @Query("SELECT * FROM photos WHERE needs_reanalysis = 1")
    fun getPhotosNeedingAnalysis(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE is_metadata_extracted = 0")
    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo>

    @Query(
        """
    SELECT id, file_path, file_name, date_added, date_taken, is_favorite 
    FROM photos 
    WHERE file_name LIKE '%' || :query || '%' 
       OR relative_path LIKE '%' || :query || '%'
    ORDER BY date_taken DESC
"""
    )
    fun searchByFileNameSummary(query: String): Flow<List<PhotoSummary>>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<Photo>): List<Long>

    @Update
    suspend fun update(photo: Photo)

    @Query("DELETE FROM photos WHERE file_path IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
