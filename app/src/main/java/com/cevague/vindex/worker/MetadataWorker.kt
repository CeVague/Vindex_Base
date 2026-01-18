package com.cevague.vindex.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MetadataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_exif),
                    "PROGRESS" to 0
                )
            )

            delay(500)

            val scanner = MediaScanner()

            val repository = (applicationContext as VindexApplication).photoRepository

            val photosNeedingMetadataExtraction = repository.getPhotosNeedingMetadataExtraction()

            val total = photosNeedingMetadataExtraction.size

            // Récupère le nombre de cœurs (ex: 4, 8, etc.)
            // val cores = Runtime.getRuntime().availableProcessors()
            // On définit un multiplicateur et on met des limites (min 5, max 50 pour garder une UI fluide)
            // val batchSize = (cores * 5).coerceIn(5, 50)

            val batchSize = (total / 33).coerceIn(5, 50)

            photosNeedingMetadataExtraction.chunked(batchSize).forEachIndexed { index, batch ->
                val enrichedBatch = batch.mapNotNull { photo ->
                    val documentFile =
                        DocumentFile.fromSingleUri(applicationContext, photo.filePath.toUri())
                    if (documentFile != null && documentFile.exists()) {
                        // 1. Extraction EXIF classique
                        var photoData =
                            scanner.createPhotoFromFile(applicationContext, documentFile, true)
                                .copy(
                                    id = photo.id,
                                    fileLastModified = photo.fileLastModified
                                )

                        // 2. Ajout du Reverse Geocoding (Lourd -> fait ici dans le Worker)
                        if (photoData.latitude != null && photoData.longitude != null) {
                            val app = applicationContext as VindexApplication
                            val cityDao = app.database.cityDao()

                            val candidates = cityDao.findNearestCity(
                                photoData.latitude,
                                photoData.longitude
                            )
                            val placeName = candidates?.let { "${it.name}, ${it.countryCode}" }
                            photoData = photoData.copy(locationName = placeName)
                        }

                        photoData
                    } else {
                        null
                    }
                }

                if (enrichedBatch.isNotEmpty()) {
                    repository.insertAll(enrichedBatch)
                }

                // Mise à jour de la progression (0 à 100)
                val processedCount = (index + 1) * batchSize
                val progress = if (processedCount >= total) 100 else (processedCount * 100 / total)
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_exif),
                        "PROGRESS" to progress
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}