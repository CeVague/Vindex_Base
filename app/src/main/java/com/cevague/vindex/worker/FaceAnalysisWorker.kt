package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.R
import com.cevague.vindex.ai.FaceEngine
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@HiltWorker
class FaceAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val personRepository: PersonRepository,
    private val photoRepository: PhotoRepository,
    private val faceEngine: FaceEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_faces),
                    "PROGRESS" to 0
                )
            )

            // Estimation d'un batch selon le nombre de coeurs
            val cores = Runtime.getRuntime().availableProcessors()
            val batchSize = (cores * 5).coerceIn(5, 50)

            // Ajustement du batch pour les appareils Low RAM
            val activityManager =
                applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isLowRamDevice = activityManager.isLowRamDevice

            // Si c'est un appareil Low RAM, on divise le batch par 2
            val finalBatchSize = if (isLowRamDevice) (batchSize / 2).coerceAtLeast(5) else batchSize

            if (BuildConfig.DEBUG) {
                val photos = photoRepository.getAllPhotosSummary().first()
                photos.take(10).forEach { photo ->
                    faceEngine.locateFaces(photo.filePath)
                }
                faceEngine.releaseSessions()
            }


            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_faces),
                    "PROGRESS" to 89
                )
            )

            delay(1000.milliseconds)

            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: SQLiteException) {
            Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}