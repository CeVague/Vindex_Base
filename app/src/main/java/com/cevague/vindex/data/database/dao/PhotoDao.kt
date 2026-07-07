package com.cevague.vindex.data.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
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

/** Album-dossier virtuel : dérivé de `photos.relative_path`. */
data class FolderSummary(
    val folderPath: String,
    val photoCount: Int,
    val coverUri: String?
)

fun Photo.toSummary(): PhotoSummary {
    return PhotoSummary(
        id = this.id,
        filePath = this.contentUri,
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

    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos WHERE relative_path = :folderPath AND is_hidden = 0 ORDER BY date_taken DESC")
    fun getPhotosSummaryByFolder(folderPath: String): Flow<List<PhotoSummary>>

    @Query("SELECT id, file_path, file_name, date_added, date_taken, is_favorite FROM photos WHERE relative_path = :folderPath AND is_hidden = 0 ORDER BY date_taken DESC")
    suspend fun getPhotosSummaryByFolderOnce(folderPath: String): List<PhotoSummary>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<Photo?>

    @Query("SELECT id, file_path, date_taken, file_name, date_added, is_favorite FROM photos WHERE id IN (:ids)")
    suspend fun getPhotosSummaryByIds(ids: List<Long>): List<PhotoSummary>

    @Query("SELECT COUNT(*) FROM photos")
    fun getPhotoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE is_hidden = 0")
    fun getVisiblePhotoCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM photos")
    suspend fun getTotalStorageUsed(): Long

    @Query("SELECT DISTINCT relative_path FROM photos WHERE relative_path IS NOT NULL ORDER BY relative_path")
    fun getAllFolders(): Flow<List<String>>

    @Query(
        """
        SELECT relative_path AS folderPath, COUNT(*) AS photoCount,
            (SELECT file_path FROM photos c
             WHERE c.relative_path = p.relative_path AND c.is_hidden = 0
             ORDER BY c.date_taken DESC LIMIT 1) AS coverUri
        FROM photos p
        WHERE relative_path IS NOT NULL AND is_hidden = 0
        GROUP BY relative_path
        ORDER BY photoCount DESC
    """
    )
    fun getFolderAlbums(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM photos WHERE is_metadata_extracted = 0")
    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo>

    @Query(
        """
    SELECT id, file_path, file_name, date_added, date_taken, is_favorite 
    FROM photos 
    WHERE file_name LIKE '%' || :query || '%' ESCAPE '\'
       OR relative_path LIKE '%' || :query || '%' ESCAPE '\'
    ORDER BY date_taken DESC
"""
    )
    fun searchByFileNameSummary(query: String): Flow<List<PhotoSummary>>

    /**
     * File de travail de l'indexation IA (reprise incrémentale, ARCHITECTURE §4.2) :
     * photos sans ligne photo_analyses pour (type, modèle). Une ligne à embedding
     * NULL (échec enregistré) compte comme traitée.
     */
    @Query(
        """
        SELECT id, file_path, file_name, date_added, date_taken, is_favorite
        FROM photos p
        WHERE p.is_hidden = 0 AND NOT EXISTS (
            SELECT 1 FROM photo_analyses a
            WHERE a.photo_id = p.id AND a.analysis_type = :type AND a.model_name = :modelName
        )
        ORDER BY p.id
        LIMIT :limit
    """
    )
    suspend fun getPhotosMissingAnalysis(type: String, modelName: String, limit: Int): List<PhotoSummary>

    @Query(
        """
        SELECT COUNT(*) FROM photos p
        WHERE p.is_hidden = 0 AND NOT EXISTS (
            SELECT 1 FROM photo_analyses a
            WHERE a.photo_id = p.id AND a.analysis_type = :type AND a.model_name = :modelName
        )
    """
    )
    suspend fun countPhotosMissingAnalysis(type: String, modelName: String): Int

    /** Villes présentes dans la galerie (format « Nom, CC »), pour QueryParser. */
    @Query("SELECT DISTINCT location_name FROM photos WHERE location_name IS NOT NULL AND is_hidden = 0")
    suspend fun getDistinctLocationNames(): List<String>

    /**
     * Filtres durs du pipeline de recherche (ARCHITECTURE.md §6). La requête est
     * construite par `buildPhotoSearchQuery` (filtres optionnels + négations +
     * jointures personnes : trop combinatoire pour un @Query statique).
     */
    @RawQuery
    suspend fun searchFilteredSummary(query: SupportSQLiteQuery): List<PhotoSummary>

    @Update
    suspend fun update(photo: Photo)

    @Upsert
    suspend fun upsertAll(photos: List<Photo>)

    @Query("DELETE FROM photos WHERE file_path IN (:contentUris)")
    suspend fun deleteByContentUris(contentUris: List<String>)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
