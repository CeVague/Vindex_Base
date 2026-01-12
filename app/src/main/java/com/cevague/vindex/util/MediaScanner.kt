package com.cevague.vindex.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
            scanDirectory(context, rootFolder, photoList)
        }

        return photoList
    }

    private fun scanDirectory(
        context: Context,
        directory: DocumentFile,
        photoList: MutableList<Photo>
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(context, file, photoList)
            } else if (file.isFile && isImage(file)) {
                val photo = createPhotoFromFile(context, file)
                photoList.add(photo)
            }
        }
    }

    private fun isImage(file: DocumentFile): Boolean {
        return file.type in imageMimeTypes
    }

    private fun createPhotoFromFile(context: Context, file: DocumentFile): Photo {


        var filePath = file.uri.toString()
        var fileName = file.name ?: "unknown"
        var folderPath = file.parentFile?.uri?.toString() ?: ""
        var fileSize = file.length()
        var mimeType = file.type
        var dateAdded = System.currentTimeMillis()
        var dateTaken = file.lastModified()

        // Extraction EXIF (à ajouter plus tard)
        var width: Int? = null
        var height: Int? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var cameraMake: String? = null
        var cameraModel: String? = null

        /*
        try {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                // Extraire les données...
            }
        } catch (e: Exception) {
            // Ignorer les erreurs EXIF
        }
        */

        return Photo(
            filePath = filePath,
            fileName = fileName,
            folderPath = folderPath,
            fileSize = fileSize,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateTaken = dateTaken
        )
    }
}