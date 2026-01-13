package com.cevague.vindex.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.cevague.vindex.data.database.entity.Photo

class MediaScanner {

    companion object {
        val imageMimeTypes = listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
            "image/gif"
        )
    }

    suspend fun scanFolder(context: Context, folderUri: Uri): List<Photo> {
        val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
        val photoList = mutableListOf<Photo>()

        if (rootFolder != null && rootFolder.exists()) {
            scanDirectory(context, rootFolder, photoList, true)
        }

        return photoList
    }

    suspend fun scanFolderShallow(context: Context, folderUri: Uri): List<Photo> {
        val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
        val photoList = mutableListOf<Photo>()

        if (rootFolder != null && rootFolder.exists()) {
            scanDirectory(context, rootFolder, photoList, false)
        }

        return photoList
    }

    private fun scanDirectory(
        context: Context,
        directory: DocumentFile,
        photoList: MutableList<Photo>,
        extractExif: Boolean
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(context, file, photoList, extractExif)
            } else if (file.isFile && isImage(file)) {
                val photo = createPhotoFromFile(context, file, extractExif)
                photoList.add(photo)
            }
        }
    }

    private fun isImage(file: DocumentFile): Boolean {
        return file.type in imageMimeTypes
    }

    fun createPhotoFromFile(context: Context, file: DocumentFile, extractExif: Boolean): Photo {
        val filePath = file.uri.toString()
        val fileName = file.name ?: "unknown"
        val folderPath = file.parentFile?.uri?.toString() ?: ""
        val fileSize = file.length()
        val mimeType = file.type
        val dateAdded = System.currentTimeMillis()
        
        if(!extractExif){
            return Photo(
                filePath = filePath,
                fileName = fileName,
                folderPath = folderPath,
                fileSize = fileSize,
                mimeType = mimeType,
                dateAdded = dateAdded,
                isMetadataExtracted = false
            )
        }
        
        // Valeurs par défaut
        var dateTaken: Long? = file.lastModified().takeIf { it > 0 }
        var width: Int? = null
        var height: Int? = null
        var orientation: Int? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var cameraMake: String? = null
        var cameraModel: String? = null

        // Extraction EXIF
        try {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                // Dimensions
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }

                // Orientation
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                // Date de prise de vue
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { dateStr ->
                    dateTaken = parseExifDate(dateStr)
                }

                // Appareil
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()

                // GPS
                val latLong = exif.latLong
                if (latLong != null) {
                    latitude = latLong[0]
                    longitude = latLong[1]
                }
            }
        } catch (e: Exception) {
            // Ignorer les erreurs EXIF (fichier corrompu, format non supporté, etc.)
        }

        return Photo(
            filePath = filePath,
            fileName = fileName,
            folderPath = folderPath,
            fileSize = fileSize,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateTaken = dateTaken,
            width = width,
            height = height,
            orientation = orientation,
            latitude = latitude,
            longitude = longitude,
            cameraMake = cameraMake,
            cameraModel = cameraModel,
            isMetadataExtracted = true
        )
    }

    // Fonction helper pour parser la date EXIF
    private fun parseExifDate(dateStr: String): Long? {
        return try {
            // Format EXIF : "2024:08:15 14:32:00"
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}
