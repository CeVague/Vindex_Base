package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository,
    private val cityRepository: CityRepository,
    private val mediaScanner: MediaScanner
) : CoroutineWorker(appContext, workerParams) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(4)

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

            val batchSize = 20

            photosToProcess.chunked(batchSize).forEachIndexed { index, batch ->
                val enrichedBatch = coroutineScope {
                    batch.map { photo ->
                        async(extractionDispatcher) {
                            try {
                                var data = mediaScanner.extractMetadata(photo)

                                if (data.latitude != null && data.longitude != null) {
                                    val city = cityRepository.findNearestCity(
                                        data.latitude!!,
                                        data.longitude!!
                                    )
                                    city?.let {
                                        data = data.copy(locationName = "${it.name}, ${it.countryCode}")
                                    }
                                }

                                val type = mediaScanner.detectMediaType(
                                    data.fileName,
                                    data.folderPath,
                                    data.width,
                                    data.height,
                                    data.cameraMake,
                                    data.cameraModel
                                )
                                data.copy(mediaType = type)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                photo
                            }
                        }
                    }.awaitAll()
                }

                photoRepository.upsertAll(enrichedBatch)

                val progress = ((index + 1) * batchSize * 100 / total).coerceAtMost(100)
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_exif),
                        "PROGRESS" to progress
                    )
                )
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Metadata extraction failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "MetadataWorker"
    }
}