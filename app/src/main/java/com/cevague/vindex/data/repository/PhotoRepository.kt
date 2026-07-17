package com.cevague.vindex.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.cevague.vindex.data.database.dao.EmbeddingRow
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.FolderSummary
import com.cevague.vindex.data.database.dao.PhotoAnalysisDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PhotoRepository.Companion.CHUNK_SIZE
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Échappe les métacaractères LIKE (`%`, `_`, `\`) d'une saisie utilisateur.
 * À utiliser avec la clause `ESCAPE '\'` des requêtes LIKE.
 */
internal fun String.escapeLikePattern(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * Réordonne des PhotoSummary selon l'ordre exact de [ids] ; les ids sans photo
 * correspondante (supprimée entre-temps) sont ignorés.
 */
internal fun reorderByIds(ids: List<Long>, summaries: List<PhotoSummary>): List<PhotoSummary> {
    val byId = summaries.associateBy { it.id }
    return ids.mapNotNull { byId[it] }
}

@Singleton
class PhotoRepository @Inject constructor(
    private val photoDao: PhotoDao,
    private val photoAnalysisDao: PhotoAnalysisDao,
    private val faceDao: FaceDao,
    private val settingsCache: SettingsCache,
    private val mediaScanner: MediaScanner
) {
    private companion object {
        const val CHUNK_SIZE = 900
        const val TAG = "PhotoRepository"
    }

    fun getAllPhotosSummary(): Flow<List<PhotoSummary>> = photoDao.getAllPhotosSummary()
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

    /**
     * Charge des PhotoSummary par ids en **préservant l'ordre** de la liste fournie
     * (SQLite `IN (...)` renvoie en ordre rowid, pas celui des ids) et en **chunkant**
     * à [CHUNK_SIZE] pour respecter la limite de variables SQLite.
     */
    suspend fun getPhotosSummaryByIdsOrdered(ids: List<Long>): List<PhotoSummary> {
        if (ids.isEmpty()) return emptyList()
        val loaded = ids.chunked(CHUNK_SIZE).flatMap { photoDao.getPhotosSummaryByIds(it) }
        return reorderByIds(ids, loaded)
    }

    fun getPhotoCount(): Flow<Int> = photoDao.getPhotoCount()
    fun getVisiblePhotoCount(): Flow<Int> = photoDao.getVisiblePhotoCount()
    fun getAllFolders(): Flow<List<String>> = photoDao.getAllFolders()

    /** Albums-dossier virtuels (dérivés de relative_path). */
    fun getFolderAlbums(): Flow<List<FolderSummary>> = photoDao.getFolderAlbums()

    fun getPhotosSummaryByFolder(folderPath: String): Flow<List<PhotoSummary>> =
        photoDao.getPhotosSummaryByFolder(folderPath)

    suspend fun getPhotosSummaryByFolderOnce(folderPath: String): List<PhotoSummary> =
        photoDao.getPhotosSummaryByFolderOnce(folderPath)

    fun getPhotosSummaryByPerson(personId: Long): Flow<List<PhotoSummary>> =
        photoDao.getPhotosSummaryByPerson(personId)

    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo> =
        photoDao.getPhotosNeedingMetadataExtraction()

    suspend fun getDistinctLocationNames(): List<String> =
        photoDao.getDistinctLocationNames()

    /** Filtres durs de la recherche (texte échappé par le builder). */
    suspend fun searchFiltered(criteria: PhotoSearchCriteria): List<PhotoSummary> {
        val (sql, args) = buildPhotoSearchQuery(criteria)
        return photoDao.searchFilteredSummary(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

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
            // Photo modifiée (retouche, rotation en place) : embedding et visages
            // décrivent l'ancienne image, et la file NOT EXISTS ne les referait
            // jamais tant que les lignes existent. On les supprime, les workers
            // de la chaîne re-analysent.
            SyncDiff.modifiedPhotoIds(batch, dbPhotosMap).forEach { photoId ->
                photoAnalysisDao.deleteAnalysesForPhoto(photoId)
                faceDao.deleteByPhoto(photoId)
            }
            totalProcessed += batch.size
            onProgress(totalProcessed)
        }

        val liveUris = mediaScanner.queryManagedUris(includedFolders)
        if (liveUris != null) {
            SyncDiff.urisToDelete(dbPhotosMap.keys, liveUris)
                .chunked(CHUNK_SIZE)
                .forEach { photoDao.deleteByContentUris(it) }
        } else {
            // Requête MediaStore indisponible : on NE supprime rien (sans liste
            // autoritative des URIs vivantes, on risquerait d'effacer des photos
            // valides). L'état périmé est rattrapé au prochain scan réussi.
            Log.w(TAG, "queryManagedUris a renvoyé null — suppression sautée ce cycle")
        }

        settingsCache.lastScanTimestamp = newSync
    }

    suspend fun upsertAll(photos: List<Photo>) = photoDao.upsertAll(photos)
    suspend fun deleteByContentUris(contentUris: List<String>) =
        contentUris.chunked(CHUNK_SIZE).forEach { photoDao.deleteByContentUris(it) }

    suspend fun deleteAll() = photoDao.deleteAll()

    // --- Analyses ---

    fun getAnalysesForPhoto(photoId: Long): Flow<List<PhotoAnalysis>> =
        photoAnalysisDao.getAnalysesForPhoto(photoId)

    suspend fun getPhotosMissingAnalysis(
        type: String,
        modelName: String,
        limit: Int
    ): List<PhotoSummary> =
        photoDao.getPhotosMissingAnalysis(type, modelName, limit)

    suspend fun countPhotosMissingAnalysis(type: String, modelName: String): Int =
        photoDao.countPhotosMissingAnalysis(type, modelName)

    suspend fun getEmbeddingsForPhotos(
        type: String,
        modelName: String,
        photoIds: List<Long>
    ): List<EmbeddingRow> =
        photoIds.chunked(CHUNK_SIZE)
            .flatMap { photoAnalysisDao.getEmbeddingsForPhotos(type, modelName, it) }

    suspend fun getEmbeddingsChunk(
        type: String,
        modelName: String,
        afterPhotoId: Long,
        limit: Int
    ): List<EmbeddingRow> =
        photoAnalysisDao.getEmbeddingsChunk(type, modelName, afterPhotoId, limit)

    suspend fun upsertAnalysis(analysis: PhotoAnalysis) =
        photoAnalysisDao.upsertAnalysis(analysis)

    suspend fun upsertAnalyses(analyses: List<PhotoAnalysis>) =
        photoAnalysisDao.upsertAnalyses(analyses)

    suspend fun deleteAnalysesByType(type: String) =
        photoAnalysisDao.deleteAnalysesByType(type)
}
