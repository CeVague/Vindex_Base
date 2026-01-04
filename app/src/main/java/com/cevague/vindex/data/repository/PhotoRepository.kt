package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.entity.Photo
import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {

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

    // Insert

    suspend fun insert(photo: Photo): Long = photoDao.insert(photo)

    suspend fun insertAll(photos: List<Photo>): List<Long> = photoDao.insertAll(photos)

    // Update

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

    // Delete

    suspend fun delete(photo: Photo) = photoDao.delete(photo)

    suspend fun deleteById(id: Long) = photoDao.deleteById(id)

    suspend fun deleteByFolder(folderPath: String) = photoDao.deleteByFolder(folderPath)

    suspend fun deleteAll() = photoDao.deleteAll()
}
