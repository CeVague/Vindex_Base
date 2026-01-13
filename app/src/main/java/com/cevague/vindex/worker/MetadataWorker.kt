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
            val cores = Runtime.getRuntime().availableProcessors()
            // On définit un multiplicateur et on met des limites (min 5, max 50 pour garder une UI fluide)
            val batchSize = (cores * 5).coerceIn(5, 50)

            photosNeedingMetadataExtraction.chunked(batchSize).forEachIndexed { index, batch ->
                val enrichedBatch = batch.mapNotNull { photo ->
                    // On récupère le fichier physique pour lire l'EXIF
                    val documentFile =
                        DocumentFile.fromSingleUri(applicationContext, photo.filePath.toUri())
                    if (documentFile != null && documentFile.exists()) {
                        // On extrait les métadonnées réelles et on injecte l'ID original pour le REPLACE
                        scanner.createPhotoFromFile(applicationContext, documentFile, true)
                            .copy(id = photo.id)
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

                delay(1000)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}