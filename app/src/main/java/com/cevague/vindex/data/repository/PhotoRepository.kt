package com.cevague.vindex.data.repository

import android.content.Context
import android.net.Uri
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoDao.FilePathAndSize
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

class PhotoRepository(
    private val photoDao: PhotoDao,
    externalScope: CoroutineScope
) {

    /**
     * Cache réactif des métadonnées de base (chemin et taille) de toutes les photos en DB.
     * Permet une comparaison (diffing) instantanée sans refaire de requête SQL complète.
     */
    val dbPhotosMetadata: StateFlow<List<FilePathAndSize>> = photoDao.getAllPathsAndSizes()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive queries

    fun getAllPhotos(): Flow<List<Photo>> = photoDao.getAllPhotos()

    fun getVisiblePhotos(): Flow<List<Photo>> = photoDao.getVisiblePhotos()

    fun getPhotosByFolder(folderPath: String): Flow<List<Photo>> =
        photoDao.getPhotosByFolder(folderPath)

    fun getPhotoById(id: Long): Flow<Photo?> = photoDao.getPhotoById(id)

    fun getPhotoCount(): Flow<Int> = photoDao.getPhotoCount()

    fun getAllFolders(): Flow<List<String>> = photoDao.getAllFolders()

    fun searchByFileName(query: String): Flow<List<Photo>> =
        photoDao.searchByFileName(query)

    fun getPhotosNeedingAnalysis(): Flow<List<Photo>> =
        photoDao.getPhotosNeedingAnalysis()

    // One-shot queries

    suspend fun getPhotoByIdOnce(id: Long): Photo? = photoDao.getPhotoByIdOnce(id)

    suspend fun getPhotoByPathOnce(filePath: String): Photo? =
        photoDao.getPhotoByPathOnce(filePath)

    suspend fun existsByPath(filePath: String): Boolean = photoDao.existsByPath(filePath)

    suspend fun getPhotosByIds(ids: List<Long>): List<Photo> = photoDao.getPhotosByIds(ids)

    suspend fun getPhotosNeedingMetadataExtraction(): List<Photo> =
        photoDao.getPhotosNeedingMetadataExtraction()

    suspend fun getLastModifiedTimestampOnce(): Long? = photoDao.getLastSyncTimestamp()


    // Synchronize
    suspend fun syncPhotos(context: Context, folderUri: Uri, onProgress: suspend (Int) -> Unit) {
        val folderPathString = folderUri.toString()
        val lastSync = photoDao.getLastSyncTimestamp() ?: 0L

        // 1. Charger les chemins actuels de ce dossier en DB
        val dbPhotos = photoDao.getAllPathsAndSizes().first()
        val folderDbMap = dbPhotos.filter { it.filePath.startsWith(folderPathString) }.associateBy { it.filePath }

        val seenPaths = mutableSetOf<String>()
        var totalNew = 0

        val scanner = MediaScanner()

        // 2. Lancer le scan
        scanner.scanFolderFast(context, folderUri, lastSync) { path ->
            seenPaths.add(path)
        }.collect { batch ->
            // Filtrage chirurgical : on n'insère QUE si c'est vraiment nouveau
            // ou si la taille/date a changé (comparaison avec folderDbMap)
            val toUpsert = batch.filter { newPhoto ->
                val existing = folderDbMap[newPhoto.filePath]
                existing == null || existing.fileSize != newPhoto.fileSize || existing.fileLastModified != newPhoto.fileLastModified
            }

            if (toUpsert.isNotEmpty()) {
                photoDao.insertAll(toUpsert)
            }

            totalNew += batch.size
            onProgress(totalNew)
        }

        // 3. Supprimer les "Fantômes" (ceux qui sont en DB mais plus sur le disque)
        val deletedPaths = folderDbMap.keys.filter { it !in seenPaths }
        if (deletedPaths.isNotEmpty()) {
            photoDao.deleteByPaths(deletedPaths)
        }
    }

    // Insert / Update / Delete (classique)

    suspend fun insert(photo: Photo): Long = photoDao.insert(photo)

    suspend fun insertAll(photos: List<Photo>): List<Long> = photoDao.insertAll(photos)

    suspend fun update(photo: Photo) = photoDao.update(photo)

    suspend fun setFavorite(id: Long, isFavorite: Boolean) =
        photoDao.setFavorite(id, isFavorite)

    suspend fun setHidden(id: Long, isHidden: Boolean) =
        photoDao.setHidden(id, isHidden)

    suspend fun updateDescription(id: Long, description: String?, model: String?) =
        photoDao.updateDescription(id, description, model, System.currentTimeMillis())

    suspend fun updateEmbedding(id: Long, embedding: ByteArray?) =
        photoDao.updateEmbedding(id, embedding)

    suspend fun updateTags(id: Long, tagsJson: String?, model: String?) =
        photoDao.updateTags(id, tagsJson, model)

    suspend fun updateOcr(id: Long, ocrText: String?, model: String?) =
        photoDao.updateOcr(id, ocrText, model)

    suspend fun markAllForReanalysis() = photoDao.markAllForReanalysis()

    suspend fun delete(photo: Photo) = photoDao.delete(photo)

    suspend fun deleteById(id: Long) = photoDao.deleteById(id)

    suspend fun deleteByPath(filePath: String) = photoDao.deleteByPath(filePath)

    suspend fun deleteByPaths(filePaths: List<String>) = photoDao.deleteByPaths(filePaths)

    suspend fun deleteByFolder(folderPath: String) = photoDao.deleteByFolder(folderPath)

    suspend fun deleteAll() = photoDao.deleteAll()
}
