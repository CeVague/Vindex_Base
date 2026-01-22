package com.cevague.vindex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import kotlinx.coroutines.delay

class DiscoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
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

            val repository = (applicationContext as VindexApplication).photoRepository

            // Synchronisation globale avec le MediaStore
            repository.syncPhotos(applicationContext) { count ->
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
