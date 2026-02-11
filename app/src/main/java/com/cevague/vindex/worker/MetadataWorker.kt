package com.cevague.vindex.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.data.repository.CityRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.util.MediaScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository,
    private val cityRepository: CityRepository,
    private val mediaScanner: MediaScanner
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_exif),
                    "PROGRESS" to 0
                )
            )

            val photosToProcess = photoRepository.getPhotosNeedingMetadataExtraction()
            val total = photosToProcess.size
            if (total == 0) return Result.success()

            // On parallélise par petits batchs pour ne pas saturer la RAM
            val batchSize = 20

            Dispatchers.IO.limitedParallelism(4)

            photosToProcess.chunked(batchSize).forEachIndexed { index, batch ->
                // Utilisation de Dispatchers.IO pour les accès fichiers + async pour le parallélisme
                val enrichedBatch = withContext(Dispatchers.IO) {
                    batch.map { photo ->
                        async {
                            try {
                                // 1. EXIF + GPS
                                var data = mediaScanner.extractMetadata(photo)

                                // 2. Geocoding
                                if (data.latitude != null && data.longitude != null) {
                                    val city = cityRepository.findNearestCity(
                                        data.latitude!!,
                                        data.longitude!!
                                    )
                                    city?.let {
                                        data =
                                            data.copy(locationName = "${it.name}, ${it.countryCode}")
                                    }
                                }

                                // 3. Media Type Refinement
                                val type = mediaScanner.detectMediaType(
                                    data.fileName,
                                    data.folderPath,
                                    data.width,
                                    data.height,
                                    data.cameraMake,
                                    data.cameraModel
                                )
                                data.copy(mediaType = type)
                            } catch (e: Exception) {
                                photo // En cas d'erreur sur une photo, on la garde telle quelle
                            }
                        }
                    }.awaitAll()
                }

                photoRepository.insertAll(enrichedBatch)

                // Update Progress
                val progress = ((index + 1) * batchSize * 100 / total).coerceAtMost(100)
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_exif),
                        "PROGRESS" to progress
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
