package com.cevague.vindex.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cevague.vindex.data.database.dao.FilePathAndSize
import com.cevague.vindex.data.database.dao.PhotoAnalysisDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.database.entity.PhotoAnalysis
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
    private val photoAnalysisDao: PhotoAnalysisDao,
    private val settingsCache: SettingsCache,
    private val mediaScanner: MediaScanner,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    private companion object {
        const val CHUNK_SIZE = 900
    }

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
    fun getVisiblePhotosSummaryPaged(): Flow<PagingData<PhotoSummary>> {
        return Pager(
            config = PagingConfig(
                pageSize = 80,
                prefetchDistance = 40,
                initialLoadSize = 120,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { photoDao.getVisiblePhotosSummaryPaged() }
        ).flow
    }

    fun getPhotoById(id: Long): Flow<Photo?> = photoDao.getPhotoById(id)
    fun getPhotosSummaryByIds(ids: List<Long>): Flow<List<PhotoSummary>> =
        photoDao.getPhotosSummaryByIds(ids)

    fun getPhotoCount(): Flow<Int> = photoDao.getPhotoCount()
    fun getVisiblePhotoCount(): Flow<Int> = photoDao.getVisiblePhotoCount()
    suspend fun getTotalStorageUsed(): Long = photoDao.getTotalStorageUsed()
    fun getAllFolders(): Flow<List<String>> = photoDao.getAllFolders()

    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo> =
        photoDao.getPhotosNeedingMetadataExtraction()

    fun searchByFileNameSummary(query: String): Flow<List<PhotoSummary>> =
        photoDao.searchByFileNameSummary(query)

    suspend fun syncPhotos(onProgress: suspend (Int) -> Unit) {
        val lastSync = settingsCache.lastScanTimestamp
        val newSync = System.currentTimeMillis()
        val includedFolders = settingsCache.includedFolders

        // Clé = filePath pour la détection de changements
        val dbPhotosMap = photoDao.getAllPathsAndSizes().first().associateBy { it.filePath }
        var totalProcessed = 0

        mediaScanner.scanMediaStore(includedFolders, lastSync).collect { batch ->
            val toUpsert = SyncDiff.photosToUpsert(batch, dbPhotosMap)
            if (toUpsert.isNotEmpty()) photoDao.upsertAll(toUpsert)
            totalProcessed += batch.size
            onProgress(totalProcessed)
        }

        mediaScanner.queryManagedUris(includedFolders)?.let { liveUris ->
            SyncDiff.urisToDelete(dbPhotosMap.keys, liveUris)
                .chunked(CHUNK_SIZE)
                .forEach { photoDao.deleteByContentUris(it) }
        }

        settingsCache.lastScanTimestamp = newSync
    }

    suspend fun update(photo: Photo) = photoDao.update(photo)
    suspend fun upsertAll(photos: List<Photo>) = photoDao.upsertAll(photos)
    suspend fun deleteByContentUris(contentUris: List<String>) =
        contentUris.chunked(CHUNK_SIZE).forEach { photoDao.deleteByContentUris(it) }
    suspend fun deleteAll() = photoDao.deleteAll()

    // --- Analyses ---

    fun getAnalysesForPhoto(photoId: Long): Flow<List<PhotoAnalysis>> =
        photoAnalysisDao.getAnalysesForPhoto(photoId)

    suspend fun upsertAnalysis(analysis: PhotoAnalysis) =
        photoAnalysisDao.upsertAnalysis(analysis)

    suspend fun upsertAnalyses(analyses: List<PhotoAnalysis>) =
        photoAnalysisDao.upsertAnalyses(analyses)
}
