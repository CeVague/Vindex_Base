package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

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

            photoRepository.syncPhotos { count ->
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_scanning),
                        "PROGRESS" to count
                    )
                )
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "DiscoveryWorker"
    }
}