package com.cevague.vindex.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MediaScanner {

    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
        )

        private val PROJECTION_WITH_GPS = PROJECTION + arrayOf(
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE
        )
    }

    fun scanMediaStore(
        context: Context,
        includedFolders: Set<String>,
        lastScanTimestamp: Long,
        onPathSeen: (String) -> Unit
    ): Flow<List<Photo>> = flow {
        val batch = mutableListOf<Photo>()
        val selection = buildSelection(includedFolders, lastScanTimestamp)
        val selectionArgs = buildSelectionArgs(includedFolders, lastScanTimestamp)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            collection,
            PROJECTION_WITH_GPS,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val latColumn = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
            val lonColumn = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                onPathSeen(contentUri.toString())

                val photo = createPhotoFromCursor(
                    cursor = cursor,
                    contentUri = contentUri,
                    idColumn = idColumn,
                    nameColumn = nameColumn,
                    dateTakenColumn = dateTakenColumn,
                    dateModifiedColumn = dateModifiedColumn,
                    sizeColumn = sizeColumn,
                    mimeColumn = mimeColumn,
                    widthColumn = widthColumn,
                    heightColumn = heightColumn,
                    relativePathColumn = relativePathColumn,
                    dataColumn = dataColumn,
                    latColumn = latColumn,
                    lonColumn = lonColumn
                )
                batch.add(photo)
                if (batch.size >= 50) {
                    emit(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) emit(batch)
    }

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

    private fun buildSelectionArgs(includedFolders: Set<String>, lastScanTimestamp: Long): Array<String>? {
        val args = mutableListOf<String>()
        if (lastScanTimestamp > 0) {
            args.add((lastScanTimestamp / 1000).toString())
        }
        includedFolders.forEach { folder ->
            args.add("%$folder%")
        }
        return if (args.isEmpty()) null else args.toTypedArray()
    }

    private fun createPhotoFromCursor(
        cursor: Cursor,
        contentUri: Uri,
        idColumn: Int,
        nameColumn: Int,
        dateTakenColumn: Int,
        dateModifiedColumn: Int,
        sizeColumn: Int,
        mimeColumn: Int,
        widthColumn: Int,
        heightColumn: Int,
        relativePathColumn: Int,
        dataColumn: Int,
        latColumn: Int,
        lonColumn: Int
    ): Photo {
        val fileName = cursor.getString(nameColumn)
        val dateModified = cursor.getLong(dateModifiedColumn) * 1000
        val dateTaken = if (dateTakenColumn >= 0) cursor.getLongOrNull(dateTakenColumn) else null
        val fileSize = cursor.getLong(sizeColumn)
        val mimeType = cursor.getString(mimeColumn)
        val width = if (widthColumn >= 0) cursor.getIntOrNull(widthColumn) else null
        val height = if (heightColumn >= 0) cursor.getIntOrNull(heightColumn) else null
        val relativePath = if (relativePathColumn >= 0) {
            cursor.getStringOrNull(relativePathColumn)?.trimEnd('/')
        } else if (dataColumn >= 0) {
            extractRelativePath(cursor.getStringOrNull(dataColumn))
        } else null

        val folderPath = relativePath ?: ""
        val latitude = if (latColumn >= 0) cursor.getDoubleOrNull(latColumn) else null
        val longitude = if (lonColumn >= 0) cursor.getDoubleOrNull(lonColumn) else null
        val mediaType = detectMediaType(fileName, folderPath, null, null)

        return Photo(
            filePath = contentUri.toString(),
            fileName = fileName,
            folderPath = folderPath,
            relativePath = relativePath,
            fileSize = fileSize,
            mimeType = mimeType,
            dateAdded = System.currentTimeMillis(),
            dateTaken = dateTaken ?: dateModified,
            fileLastModified = dateModified,
            width = width,
            height = height,
            latitude = latitude,
            longitude = longitude,
            mediaType = mediaType,
            isMetadataExtracted = false
        )
    }

    /**
     * Extraction approfondie via ExifInterface (GPS original garanti sur Android 10+).
     */
    fun extractMetadata(context: Context, photo: Photo): Photo {
        val uri = Uri.parse(photo.filePath)
        var latitude = photo.latitude
        var longitude = photo.longitude
        var cameraMake: String? = null
        var cameraModel: String? = null
        var orientation: Int? = null

        try {
            val uriToRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { MediaStore.setRequireOriginal(uri) } catch (e: Exception) { uri }
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
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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

    private fun detectMediaType(fileName: String, folderPath: String, cameraMake: String?, cameraModel: String?): String {
        return when {
            fileName.contains("Screenshot", ignoreCase = true) ||
                    folderPath.contains("Screenshots", ignoreCase = true) -> "screenshot"
            cameraMake == null && cameraModel == null -> "other"
            else -> "photo"
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

    suspend fun listImageFolders(context: Context): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        val projection = arrayOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(projection[0])
            while (cursor.moveToNext()) {
                val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getStringOrNull(pathColumn)?.trimEnd('/') else extractRelativePath(cursor.getStringOrNull(pathColumn))
                if (path != null) folders[path] = (folders[path] ?: 0) + 1
            }
        }
        folders.map { FolderInfo(it.key, it.value) }.sortedByDescending { it.photoCount }
    }

    data class FolderInfo(val relativePath: String, val photoCount: Int)

    private fun Cursor.getStringOrNull(columnIndex: Int): String? = if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    private fun Cursor.getLongOrNull(columnIndex: Int): Long? = if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    private fun Cursor.getIntOrNull(columnIndex: Int): Int? = if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    private fun Cursor.getDoubleOrNull(columnIndex: Int): Double? = if (columnIndex >= 0 && !isNull(columnIndex)) getDouble(columnIndex).takeIf { it != 0.0 } else null
}
