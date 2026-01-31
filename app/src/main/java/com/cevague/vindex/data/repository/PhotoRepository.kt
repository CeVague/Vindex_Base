package com.cevague.vindex.data.repository

import android.content.Context
import com.cevague.vindex.data.database.dao.FilePathAndSize
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.di.ApplicationScope
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val photoDao: PhotoDao,
    private val settingsCache: SettingsCache,
    private val mediaScanner: MediaScanner,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    val dbPhotosMetadata: StateFlow<List<FilePathAndSize>> = photoDao.getAllPathsAndSizes()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getAllPhotos(): Flow<List<Photo>> = photoDao.getAllPhotos()
    fun getAllPhotosSummary(): Flow<List<PhotoSummary>> = photoDao.getAllPhotosSummary()
    fun getVisiblePhotos(): Flow<List<Photo>> = photoDao.getVisiblePhotos()
    fun getVisiblePhotosSummary(): Flow<List<PhotoSummary>> = photoDao.getVisiblePhotosSummary()
    fun getPhotoById(id: Long): Flow<Photo?> = photoDao.getPhotoById(id)
    fun getPhotoCount(): Flow<Int> = photoDao.getPhotoCount()
    fun getVisiblePhotoCount(): Flow<Int> = photoDao.getVisiblePhotoCount()
    suspend fun getTotalStorageUsed(): Long = photoDao.getTotalStorageUsed()
    fun getAllFolders(): Flow<List<String>> = photoDao.getAllFolders()

    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo> =
        photoDao.getPhotosNeedingMetadataExtraction()

    fun searchByFileNameSummary(query: String): Flow<List<PhotoSummary>> =
        photoDao.searchByFileNameSummary(query)

    suspend fun syncPhotos(context: Context, onProgress: suspend (Int) -> Unit) {
        val lastSync = settingsCache.lastScanTimestamp
        val newSync = System.currentTimeMillis()
        val includedFolders = settingsCache.includedFolders

        // 1. Charger les métadonnées actuelles pour le diffing
        val dbPhotosMap = photoDao.getAllPathsAndSizes().first().associateBy { it.filePath }
        val seenPaths = mutableSetOf<String>()
        var totalProcessed = 0

        // 2. Scan du MediaStore
        mediaScanner.scanMediaStore(includedFolders, lastSync) { path ->
            seenPaths.add(path)
        }.collect { batch ->
            val toUpsert = batch.filter { newPhoto ->
                val existing = dbPhotosMap[newPhoto.filePath]
                existing == null || existing.fileSize != newPhoto.fileSize || existing.fileLastModified != newPhoto.fileLastModified
            }

            if (toUpsert.isNotEmpty()) {
                photoDao.insertAll(toUpsert)
            }

            totalProcessed += batch.size
            onProgress(totalProcessed)
        }

        if (seenPaths.isNotEmpty() || lastSync == 0L) {
            val pathsToRemove = dbPhotosMap.keys.filter { path ->
                val isInManagedFolder = includedFolders.any { folder -> path.contains(folder) }
                isInManagedFolder && path !in seenPaths
            }

            if (pathsToRemove.isNotEmpty()) {
                photoDao.deleteByPaths(pathsToRemove)
            }
        }

        settingsCache.lastScanTimestamp = newSync
    }

    suspend fun insertAll(photos: List<Photo>) = photoDao.insertAll(photos)
    suspend fun update(photo: Photo) = photoDao.update(photo)
    suspend fun deleteByPaths(filePaths: List<String>) = photoDao.deleteByPaths(filePaths)
    suspend fun deleteAll() = photoDao.deleteAll()
}
