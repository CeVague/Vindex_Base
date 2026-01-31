package com.cevague.vindex.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class DiscoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_scanning),
                    "PROGRESS" to 0
                )
            )

            // Petit délai pour laisser l'UI respirer
            delay(500)

            // Synchronisation globale avec le MediaStore
            photoRepository.syncPhotos(applicationContext) { count ->
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_scanning),
                        "PROGRESS" to count
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
