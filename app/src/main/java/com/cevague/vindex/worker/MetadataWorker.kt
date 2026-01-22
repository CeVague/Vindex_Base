package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.delay

class MetadataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_exif),
                    "PROGRESS" to 0
                )
            )

            delay(500)

            val scanner = MediaScanner()
            val repository = (applicationContext as VindexApplication).photoRepository
            val cityRepository = (applicationContext as VindexApplication).cityRepository

            val photosNeedingMetadataExtraction = repository.getPhotosNeedingMetadataExtraction()
            val total = photosNeedingMetadataExtraction.size

            if (total == 0) return Result.success()

            val batchSize = (total / 33).coerceIn(5, 50)

            photosNeedingMetadataExtraction.chunked(batchSize).forEachIndexed { index, batch ->
                val enrichedBatch = batch.map { photo ->
                    // Extraction EXIF + GPS via MediaStore (RequireOriginal)
                    var photoData = scanner.extractMetadata(applicationContext, photo)

                    // Reverse Geocoding
                    if (photoData.latitude != null && photoData.longitude != null) {
                        val candidates = cityRepository.findNearestCity(
                            photoData.latitude!!,
                            photoData.longitude!!
                        )
                        val placeName = candidates?.let { "${it.name}, ${it.countryCode}" }
                        photoData = photoData.copy(locationName = placeName)
                    }
                    photoData
                }

                if (enrichedBatch.isNotEmpty()) {
                    repository.insertAll(enrichedBatch)
                }

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
        } catch (e: SQLiteException) {
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
