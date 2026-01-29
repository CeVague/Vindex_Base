package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.*

class MetadataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_exif), "PROGRESS" to 0))

            val scanner = MediaScanner()
            val repository = (applicationContext as VindexApplication).photoRepository
            val cityRepository = (applicationContext as VindexApplication).cityRepository

            val photosToProcess = repository.getPhotosNeedingMetadataExtraction()
            val total = photosToProcess.size
            if (total == 0) return Result.success()

            // On parallélise par petits batchs pour ne pas saturer la RAM
            val batchSize = 20 
            
            photosToProcess.chunked(batchSize).forEachIndexed { index, batch ->
                // Utilisation de Dispatchers.IO pour les accès fichiers + async pour le parallélisme
                val enrichedBatch = withContext(Dispatchers.IO) {
                    batch.map { photo ->
                        async {
                            try {
                                // 1. EXIF + GPS
                                var data = scanner.extractMetadata(applicationContext, photo)

                                // 2. Geocoding
                                if (data.latitude != null && data.longitude != null) {
                                    val city = cityRepository.findNearestCity(data.latitude!!, data.longitude!!)
                                    city?.let { data = data.copy(locationName = "${it.name}, ${it.countryCode}") }
                                }

                                // 3. Media Type Refinement
                                val type = scanner.detectMediaType(data.fileName, data.folderPath, data.width, data.height, data.cameraMake, data.cameraModel)
                                data.copy(mediaType = type)
                            } catch (e: Exception) {
                                photo // En cas d'erreur sur une photo, on la garde telle quelle
                            }
                        }
                    }.awaitAll()
                }

                repository.insertAll(enrichedBatch)

                // Update Progress
                val progress = ((index + 1) * batchSize * 100 / total).coerceAtMost(100)
                setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_exif), "PROGRESS" to progress))
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
