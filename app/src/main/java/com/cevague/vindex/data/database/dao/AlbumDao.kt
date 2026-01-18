package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cevague.vindex.data.database.entity.Album
import com.cevague.vindex.data.database.entity.AlbumPhoto
import com.cevague.vindex.data.database.entity.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    // Album queries - reactive

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE album_type = :type ORDER BY name ASC")
    fun getAlbumsByType(type: String): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE album_type = 'folder' ORDER BY name ASC")
    fun getFolderAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE album_type = 'manual' ORDER BY name ASC")
    fun getManualAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE album_type LIKE 'auto_%' ORDER BY name ASC")
    fun getAutoAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun getAlbumById(id: Long): Flow<Album?>

    @Query("SELECT COUNT(*) FROM albums")
    fun getAlbumCount(): Flow<Int>

    // Album queries - one-shot

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumByIdOnce(id: Long): Album?

    @Query("SELECT * FROM albums WHERE folder_path = :folderPath AND album_type = 'folder' LIMIT 1")
    suspend fun getAlbumByFolderPath(folderPath: String): Album?

    @Query("SELECT * FROM albums WHERE name = :name AND album_type = :type LIMIT 1")
    suspend fun getAlbumByNameAndType(name: String, type: String): Album?

    // Photos in album

    @Query(
        """
        SELECT p.* FROM photos p
        INNER JOIN album_photos ap ON p.id = ap.photo_id
        WHERE ap.album_id = :albumId
        ORDER BY p.date_taken DESC
    """
    )
    fun getPhotosInAlbum(albumId: Long): Flow<List<Photo>>

    @Query(
        """
        SELECT p.* FROM photos p
        INNER JOIN album_photos ap ON p.id = ap.photo_id
        WHERE ap.album_id = :albumId
        ORDER BY p.date_taken DESC
    """
    )
    suspend fun getPhotosInAlbumOnce(albumId: Long): List<Photo>

    @Query(
        """
        SELECT COUNT(*) FROM album_photos WHERE album_id = :albumId
    """
    )
    fun getPhotoCountInAlbum(albumId: Long): Flow<Int>

    // Albums containing photo

    @Query(
        """
        SELECT a.* FROM albums a
        INNER JOIN album_photos ap ON a.id = ap.album_id
        WHERE ap.photo_id = :photoId
    """
    )
    fun getAlbumsContainingPhoto(photoId: Long): Flow<List<Album>>

    // Album insert/update/delete

    @Insert
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE albums SET cover_photo_id = :photoId WHERE id = :id")
    suspend fun updateCover(id: Long, photoId: Long?)

    @Query("UPDATE albums SET photo_count = :count WHERE id = :id")
    suspend fun updatePhotoCount(id: Long, count: Int)

    @Query(
        """
        UPDATE albums SET photo_count = (
            SELECT COUNT(*) FROM album_photos ap WHERE ap.album_id = albums.id
        )
    """
    )
    suspend fun recalculateAllPhotoCounts()

    @Query(
        """
        UPDATE albums SET photo_count = (
            SELECT COUNT(*) FROM album_photos ap WHERE ap.album_id = :id
        ) WHERE id = :id
    """
    )
    suspend fun recalculatePhotoCount(id: Long)

    @Delete
    suspend fun delete(album: Album)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM albums WHERE album_type = :type")
    suspend fun deleteByType(type: String)

    // AlbumPhoto junction table operations

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun addPhotoToAlbum(albumPhoto: AlbumPhoto)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun addPhotosToAlbum(albumPhotos: List<AlbumPhoto>)

    @Query("DELETE FROM album_photos WHERE album_id = :albumId AND photo_id = :photoId")
    suspend fun removePhotoFromAlbum(albumId: Long, photoId: Long)

    @Query("DELETE FROM album_photos WHERE album_id = :albumId")
    suspend fun removeAllPhotosFromAlbum(albumId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM album_photos WHERE album_id = :albumId AND photo_id = :photoId)")
    suspend fun isPhotoInAlbum(albumId: Long, photoId: Long): Boolean
}
