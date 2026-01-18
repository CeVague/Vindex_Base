package com.cevague.vindex.util

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.cevague.vindex.data.database.entity.Photo
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaScanner {

    companion object {
        val imageMimeTypes = listOf(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/gif"
        )
    }

    /**
     * Scan ultra rapide via DocumentsContract.
     */
    fun scanFolderFast(
        context: Context,
        rootUri: Uri,
        lastScanTimestamp: Long,
        onPathSeen: (String) -> Unit
    ): Flow<List<Photo>> = flow {
        val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
        val batch = mutableListOf<Photo>()
        val stack = mutableListOf(rootDocId)

        while (stack.isNotEmpty()) {
            val docId = stack.removeAt(stack.size - 1)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId)

            context.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                val idIdx =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val dateIdx =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val nameIdx =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx)
                    val mime = cursor.getString(mimeIdx)
                    val lastModified = cursor.getLong(dateIdx)
                    val fileUri =
                        DocumentsContract.buildDocumentUriUsingTree(rootUri, childId).toString()

                    onPathSeen(fileUri)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.add(childId)
                    } else if (mime in imageMimeTypes && lastModified > lastScanTimestamp) {
                        val parentUri =
                            DocumentsContract.buildDocumentUriUsingTree(rootUri, docId).toString()
                        batch.add(
                            createPhotoFromCursor(
                                cursor,
                                fileUri,
                                parentUri,
                                nameIdx,
                                sizeIdx,
                                mimeIdx,
                                dateIdx
                            )
                        )
                        if (batch.size >= 50) {
                            emit(batch.toList()); batch.clear()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) emit(batch)
    }

    private fun createPhotoFromCursor(
        cursor: Cursor,
        fileUri: String,
        parentUri: String,
        nameIdx: Int,
        sizeIdx: Int,
        mimeIdx: Int,
        dateIdx: Int
    ): Photo {
        return Photo(
            filePath = fileUri,
            fileName = cursor.getString(nameIdx),
            folderPath = parentUri,
            fileSize = cursor.getLong(sizeIdx),
            mimeType = cursor.getString(mimeIdx),
            dateAdded = System.currentTimeMillis(),
            dateTaken = cursor.getLong(dateIdx).takeIf { it > 0 },
            fileLastModified = cursor.getLong(dateIdx),
            isMetadataExtracted = false
        )
    }

    fun createPhotoFromFile(context: Context, file: DocumentFile, extractExif: Boolean): Photo {
        val filePath = file.uri.toString()
        val fileName = file.name ?: "unknown"
        val folderPath = file.parentFile?.uri?.toString() ?: ""
        val fileSize = file.length()
        var mimeType = file.type
        val dateAdded = System.currentTimeMillis()
        val fileLastModified = file.lastModified()

        if (!extractExif) {
            return Photo(
                filePath = filePath, fileName = fileName, folderPath = folderPath,
                fileSize = fileSize, mimeType = mimeType, dateAdded = dateAdded,
                fileLastModified = fileLastModified, isMetadataExtracted = false
            )
        }

        var dateTaken: Long? = fileLastModified.takeIf { it > 0 }
        var width: Int? = null
        var height: Int? = null
        var orientation: Int? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var cameraMake: String? = null
        var cameraModel: String? = null
        var mediaType = "photo"



        try {
            // 1. DIMENSIONS (La méthode la plus rapide pour TOUS les formats dont PNG)
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                width = options.outWidth
                height = options.outHeight
                mimeType = options.outMimeType ?: mimeType
            }

            // 2. MÉTADONNÉES (metadata-extractor est plus complet pour PNG/WebP/HEIC)
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val metadata = ImageMetadataReader.readMetadata(inputStream)

                // Appareil & Orientation
                metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)?.let {
                    cameraMake = it.getString(ExifIFD0Directory.TAG_MAKE)?.trim()
                    cameraModel = it.getString(ExifIFD0Directory.TAG_MODEL)?.trim()
                    orientation = it.getInteger(ExifIFD0Directory.TAG_ORIENTATION)

                    // Détection Screenshot et autre via le champ "Software" (Logiciel)
                    // Les captures d'écran système marquent souvent leur origine ici
                    it.getString(ExifIFD0Directory.TAG_SOFTWARE)?.let { software ->
                        when {
                            software.contains("Screenshot", ignoreCase = true) -> {
                                mediaType = "screenshot"
                            }

                            software.contains("Snapseed", ignoreCase = true) ||
                                    software.contains("Photoshop", ignoreCase = true) -> {
                                mediaType = "edited"
                            }

                            software.contains("Instagram", ignoreCase = true) -> {
                                mediaType = "social"
                            }
                        }
                    }

                }

                // Date de prise de vue (souvent dans SubIFD)
                metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.let {
                    dateTaken =
                        it.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.time ?: dateTaken

                    // DÉTECTION SELFIE : On cherche "front" dans le modèle de l'objectif
                    // metadata-extractor extrait souvent le nom de la caméra utilisée comme "Lens Model"
                    it.getString(ExifSubIFDDirectory.TAG_LENS_MODEL)?.let { lens ->
                        if (lens.contains("front", ignoreCase = true) || lens.contains(
                                "avant",
                                ignoreCase = true
                            )
                        ) {
                            mediaType = "selfie"
                        }
                    }
                }

                // GPS
                metadata.getFirstDirectoryOfType(GpsDirectory::class.java)?.let {
                    it.geoLocation?.let { loc ->
                        latitude = loc.latitude
                        longitude = loc.longitude
                    }
                }
            }

            // 3. Détection Capture d'écran de rattrapage (via le nom ou le chemin)
            // Détection Screenshot par nom/chemin
            if (mediaType == "photo") {
                if (fileName.contains("Screenshot", ignoreCase = true) ||
                    folderPath.contains("Screenshots", ignoreCase = true)
                ) {
                    mediaType = "screenshot"
                }
            }

            // 4. Détection autre
            if (mediaType == "photo" && cameraMake == null && cameraModel == null) {
                mediaType = "other"
            }

            // 5. FALLBACK : Utiliser ExifInterface UNIQUEMENT si infos vitales manquantes
            // Récupération GPS (plus robuste sur Android)
            if (latitude == null) {
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { fallbackStream ->
                        val exif = androidx.exifinterface.media.ExifInterface(fallbackStream)

                        exif.latLong?.let {
                            latitude = it[0]
                            longitude = it[1]
                        }
                    }
                } catch (e: Exception) { /* Ignorer */ }
            }

        } catch (e: Exception) {
            Log.e("MediaScanner", "Erreur extraction: ${file.name}", e)
        }

        return Photo(
            filePath = filePath, fileName = fileName, folderPath = folderPath,
            fileSize = fileSize, mimeType = mimeType, dateAdded = dateAdded,
            dateTaken = dateTaken, width = width, height = height,
            orientation = orientation, latitude = latitude, longitude = longitude,
            cameraMake = cameraMake, cameraModel = cameraModel,
            fileLastModified = fileLastModified, isMetadataExtracted = true,
            mediaType = mediaType
        )
    }
}