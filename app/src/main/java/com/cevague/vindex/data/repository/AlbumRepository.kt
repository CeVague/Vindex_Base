package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.AlbumDao
import com.cevague.vindex.data.database.entity.Album
import com.cevague.vindex.data.database.entity.AlbumPhoto
import com.cevague.vindex.data.database.entity.Photo
import kotlinx.coroutines.flow.Flow

class AlbumRepository(private val albumDao: AlbumDao) {

    // Album queries - reactive

    fun getAllAlbums(): Flow<List<Album>> = albumDao.getAllAlbums()

    fun getAlbumsByType(type: String): Flow<List<Album>> = albumDao.getAlbumsByType(type)

    fun getFolderAlbums(): Flow<List<Album>> = albumDao.getFolderAlbums()

    fun getManualAlbums(): Flow<List<Album>> = albumDao.getManualAlbums()

    fun getAutoAlbums(): Flow<List<Album>> = albumDao.getAutoAlbums()

    fun getAlbumById(id: Long): Flow<Album?> = albumDao.getAlbumById(id)

    fun getAlbumCount(): Flow<Int> = albumDao.getAlbumCount()

    fun getPhotosInAlbum(albumId: Long): Flow<List<Photo>> = albumDao.getPhotosInAlbum(albumId)

    fun getPhotoCountInAlbum(albumId: Long): Flow<Int> = albumDao.getPhotoCountInAlbum(albumId)

    fun getAlbumsContainingPhoto(photoId: Long): Flow<List<Album>> =
        albumDao.getAlbumsContainingPhoto(photoId)

    // Album queries - one-shot

    suspend fun getAlbumByIdOnce(id: Long): Album? = albumDao.getAlbumByIdOnce(id)

    suspend fun getAlbumByFolderPath(folderPath: String): Album? =
        albumDao.getAlbumByFolderPath(folderPath)

    suspend fun getPhotosInAlbumOnce(albumId: Long): List<Photo> =
        albumDao.getPhotosInAlbumOnce(albumId)

    suspend fun isPhotoInAlbum(albumId: Long, photoId: Long): Boolean =
        albumDao.isPhotoInAlbum(albumId, photoId)

    // Album creation

    suspend fun insert(album: Album): Long = albumDao.insert(album)

    suspend fun createFolderAlbum(name: String, folderPath: String): Long {
        val album = Album(
            name = name,
            albumType = Album.TYPE_FOLDER,
            folderPath = folderPath,
            createdAt = System.currentTimeMillis()
        )
        return albumDao.insert(album)
    }

    suspend fun createManualAlbum(name: String): Long {
        val album = Album(
            name = name,
            albumType = Album.TYPE_MANUAL,
            createdAt = System.currentTimeMillis()
        )
        return albumDao.insert(album)
    }

    suspend fun getOrCreateFolderAlbum(name: String, folderPath: String): Long {
        val existing = albumDao.getAlbumByFolderPath(folderPath)
        return existing?.id ?: createFolderAlbum(name, folderPath)
    }

    // Album update

    suspend fun update(album: Album) = albumDao.update(album)

    suspend fun updateName(id: Long, name: String) = albumDao.updateName(id, name)

    suspend fun updateCover(id: Long, photoId: Long?) = albumDao.updateCover(id, photoId)

    // Album delete

    suspend fun delete(album: Album) = albumDao.delete(album)

    suspend fun deleteById(id: Long) = albumDao.deleteById(id)

    suspend fun deleteByType(type: String) = albumDao.deleteByType(type)

    // Photo <-> Album management

    suspend fun addPhotoToAlbum(albumId: Long, photoId: Long) {
        val albumPhoto = AlbumPhoto(
            albumId = albumId,
            photoId = photoId,
            addedAt = System.currentTimeMillis()
        )
        albumDao.addPhotoToAlbum(albumPhoto)
        albumDao.recalculatePhotoCount(albumId)
    }

    suspend fun addPhotosToAlbum(albumId: Long, photoIds: List<Long>) {
        val albumPhotos = photoIds.map { photoId ->
            AlbumPhoto(
                albumId = albumId,
                photoId = photoId,
                addedAt = System.currentTimeMillis()
            )
        }
        albumDao.addPhotosToAlbum(albumPhotos)
        albumDao.recalculatePhotoCount(albumId)
    }

    suspend fun removePhotoFromAlbum(albumId: Long, photoId: Long) {
        albumDao.removePhotoFromAlbum(albumId, photoId)
        albumDao.recalculatePhotoCount(albumId)
    }

    suspend fun removeAllPhotosFromAlbum(albumId: Long) {
        albumDao.removeAllPhotosFromAlbum(albumId)
        albumDao.updatePhotoCount(albumId, 0)
    }
}
