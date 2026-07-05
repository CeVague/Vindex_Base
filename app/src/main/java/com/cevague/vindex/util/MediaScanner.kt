package com.cevague.vindex.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.cevague.vindex.data.database.entity.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Date effective d'une photo pour le tri et les en-têtes : la date de prise de
 * vue EXIF si elle est exploitable, sinon la date de modification du fichier.
 * MediaStore renvoie fréquemment `DATE_TAKEN` = 0 (et non null) pour les images
 * sans EXIF (screenshots) : ce cas est traité comme absent.
 */
internal fun resolveDateTaken(rawDateTaken: Long?, fileModifiedMillis: Long): Long =
    rawDateTaken?.takeIf { it > 0 } ?: fileModifiedMillis

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // LATITUDE/LONGITUDE expurgées par MediaStore depuis l'API 29 (toujours
        // nulles) : les vraies coordonnées viennent de l'EXIF via MetadataWorker.
        // IS_TRASHED inutile : MediaStore exclut la corbeille des requêtes par défaut.
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.IS_FAVORITE
        )
    }

    fun contentUriFor(id: Long): String =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()

    private fun imagesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    fun scanMediaStore(
        includedFolders: Set<String>,
        lastScanTimestamp: Long
    ): Flow<List<Photo>> = flow {
        val batch = mutableListOf<Photo>()
        val selection = buildSelection(includedFolders, lastScanTimestamp)
        val selectionArgs = buildSelectionArgs(includedFolders, lastScanTimestamp)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val collection = imagesCollection()

        context.contentResolver.query(
            collection,
            PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val isFavoriteColumn = cursor.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = contentUriFor(id)

                val photo = createPhotoFromCursor(
                    cursor = cursor,
                    id = id,
                    contentUri = contentUri,
                    nameColumn = nameColumn,
                    dateTakenColumn = dateTakenColumn,
                    dateAddedColumn = dateAddedColumn,
                    dateModifiedColumn = dateModifiedColumn,
                    sizeColumn = sizeColumn,
                    mimeColumn = mimeColumn,
                    widthColumn = widthColumn,
                    heightColumn = heightColumn,
                    orientationColumn = orientationColumn,
                    relativePathColumn = relativePathColumn,
                    dataColumn = dataColumn,
                    isFavoriteColumn = isFavoriteColumn
                )
                batch.add(photo)
                if (batch.size >= 50) {
                    emit(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) emit(batch)
    }.flowOn(Dispatchers.IO)

    private fun buildSelection(includedFolders: Set<String>, lastScanTimestamp: Long): String {
        val conditions = mutableListOf<String>()
        if (lastScanTimestamp > 0) {
            conditions.add("${MediaStore.Images.Media.DATE_MODIFIED} > ?")
        }
        if (includedFolders.isNotEmpty()) {
            val folderConditions = includedFolders.map {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                } else {
                    "${MediaStore.Images.Media.DATA} LIKE ?"
                }
            }
            conditions.add("(${folderConditions.joinToString(" OR ")})")
        }
        return if (conditions.isEmpty()) "" else conditions.joinToString(" AND ")
    }

    private fun buildSelectionArgs(
        includedFolders: Set<String>,
        lastScanTimestamp: Long
    ): Array<String>? {
        val args = mutableListOf<String>()
        if (lastScanTimestamp > 0) {
            args.add((lastScanTimestamp / 1000).toString())
        }
        includedFolders.forEach { folder ->
            // Matching ancré, pas "contains" : "DCIM/Camera" ne doit pas
            // capturer "DCIM/CameraBackup".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                args.add("$folder/%")      // RELATIVE_PATH : préfixe du dossier
            } else {
                args.add("%/$folder/%")    // DATA (absolu) : segment de chemin borné
            }
        }
        return if (args.isEmpty()) null else args.toTypedArray()
    }

    private fun createPhotoFromCursor(
        cursor: Cursor,
        id: Long,
        contentUri: String,
        nameColumn: Int,
        dateTakenColumn: Int,
        dateAddedColumn: Int,
        dateModifiedColumn: Int,
        sizeColumn: Int,
        mimeColumn: Int,
        widthColumn: Int,
        heightColumn: Int,
        orientationColumn: Int,
        relativePathColumn: Int,
        dataColumn: Int,
        isFavoriteColumn: Int,
    ): Photo {
        val fileName = cursor.getString(nameColumn)
        val dateModified = cursor.getLong(dateModifiedColumn) * 1000
        val dateAdded = cursor.getLong(dateAddedColumn) * 1000
        val dateTaken = if (dateTakenColumn >= 0) cursor.getLongOrNull(dateTakenColumn) else null
        val fileSize = cursor.getLong(sizeColumn)
        val mimeType = cursor.getString(mimeColumn)
        val width = if (widthColumn >= 0) cursor.getIntOrNull(widthColumn) else null
        val height = if (heightColumn >= 0) cursor.getIntOrNull(heightColumn) else null
        val orientation =
            if (orientationColumn >= 0) cursor.getIntOrNull(orientationColumn) else null
        val relativePath = if (relativePathColumn >= 0) {
            cursor.getStringOrNull(relativePathColumn)?.trimEnd('/')
        } else if (dataColumn >= 0) {
            extractRelativePath(cursor.getStringOrNull(dataColumn))
        } else null

        val folderPath = relativePath ?: ""
        val isFavorite = cursor.getIntOrNull(isFavoriteColumn) == 1
        val mediaType = detectMediaType(fileName, folderPath, width, height, null, null)

        return Photo(
            id = id,
            contentUri = contentUri,
            fileName = fileName,
            folderPath = folderPath,
            relativePath = relativePath,
            fileSize = fileSize,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateTaken = resolveDateTaken(dateTaken, dateModified),
            fileLastModified = dateModified,
            width = width,
            height = height,
            orientation = orientation,
            isFavorite = isFavorite,
            mediaType = mediaType,
            isMetadataExtracted = false
        )
    }

    /**
     * Extraction approfondie via ExifInterface (GPS original garanti sur Android 10+).
     */
    fun extractMetadata(photo: Photo): Photo {
        val uri = photo.contentUri.toUri()
        var latitude = photo.latitude
        var longitude = photo.longitude
        var cameraMake: String? = null
        var cameraModel: String? = null
        var orientation: Int? = null

        try {
            val uriToRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    MediaStore.setRequireOriginal(uri)
                } catch (e: Exception) {
                    uri
                }
            } else uri

            context.contentResolver.openInputStream(uriToRead)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                if (latLong != null && (latLong[0] != 0.0 || latLong[1] != 0.0)) {
                    latitude = latLong[0]
                    longitude = latLong[1]
                }
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Erreur EXIF pour ${photo.fileName}", e)
        }

        return photo.copy(
            latitude = latitude,
            longitude = longitude,
            cameraMake = cameraMake,
            cameraModel = cameraModel,
            orientation = orientation,
            isMetadataExtracted = true
        )
    }

    fun detectMediaType(
        fileName: String,
        relativePath: String?,
        width: Int?,
        height: Int?,
        cameraMake: String?,
        cameraModel: String?
    ): Int {
        val path = relativePath?.lowercase() ?: ""
        val name = fileName.lowercase()

        return when {
            // 1. Captures d'écran
            name.contains("screenshot") || path.contains("screenshots") -> Photo.MEDIA_TYPE_SCREENSHOT

            // 2. Documents & Scans
            path.contains("scan") || path.contains("adobe scan") || path.contains("office lens") -> Photo.MEDIA_TYPE_DOCUMENT

            // 3. Réseaux Sociaux & Messageries
            path.contains("whatsapp") || path.contains("signal") || name.contains("signal") || path.contains(
                "telegram"
            ) -> Photo.MEDIA_TYPE_SOCIAL

            // 4. Rafales (Burst)
            name.contains("burst") || name.contains("_seq_") -> Photo.MEDIA_TYPE_BURST

            // 7. Panoramas & Formats spécifiques (nécessite dimensions)
            width != null && height != null && width > 0 && height > 0 -> {
                val ratio = width.toFloat() / height.toFloat()
                val absRatio = if (ratio < 1f) 1f / ratio else ratio

                when {
                    absRatio > 2.2f -> Photo.MEDIA_TYPE_PANORAMA
                    absRatio == 1f -> Photo.MEDIA_TYPE_SQUARE
                    // Selfie probable si DCIM/Camera et Front Camera (nécessite MetadataWorker)
                    cameraModel?.lowercase()?.contains("front") == true -> Photo.MEDIA_TYPE_SELFIE
                    else -> Photo.MEDIA_TYPE_PHOTO
                }
            }

            // 8. Origine Inconnue (Web / App tierce)
            cameraMake == null && cameraModel == null -> Photo.MEDIA_TYPE_OTHER

            else -> Photo.MEDIA_TYPE_PHOTO
        }
    }

    private fun extractRelativePath(absolutePath: String?): String? {
        if (absolutePath == null) return null
        val markers = listOf("/DCIM/", "/Pictures/", "/Download/", "/Documents/")
        for (marker in markers) {
            val idx = absolutePath.indexOf(marker)
            if (idx >= 0) {
                val relativePart = absolutePath.substring(idx + 1)
                return relativePart.substringBeforeLast('/')
            }
        }
        return null
    }

    suspend fun listImageFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        val projection =
            arrayOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA)
        val collection = imagesCollection()

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(projection[0])
            while (cursor.moveToNext()) {
                val path =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getStringOrNull(
                        pathColumn
                    )?.trimEnd('/') else extractRelativePath(cursor.getStringOrNull(pathColumn))
                if (path != null) folders[path] = (folders[path] ?: 0) + 1
            }
        }
        folders.map { FolderInfo(it.key, it.value) }.sortedByDescending { it.photoCount }
    }

    suspend fun queryManagedUris(includedFolders: Set<String>): Set<String>? =
        withContext(Dispatchers.IO) {
            val cursor = context.contentResolver.query(
                imagesCollection(),
                arrayOf(MediaStore.Images.Media._ID),
                buildSelection(includedFolders, 0L),
                buildSelectionArgs(includedFolders, 0L),
                null
            ) ?: return@withContext null
            cursor.use { c ->
                buildSet(c.count) {
                    val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (c.moveToNext()) add(contentUriFor(c.getLong(idColumn)))
                }
            }
        }

    data class FolderInfo(val relativePath: String, val photoCount: Int)

    private fun Cursor.getStringOrNull(columnIndex: Int): String? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
}
