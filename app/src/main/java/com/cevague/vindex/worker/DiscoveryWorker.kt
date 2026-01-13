package com.cevague.vindex.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DiscoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_scanning),
                    "PROGRESS" to 0
                )
            )

            val folderUriString =
                inputData.getString("FOLDER_URI") ?: return@withContext Result.failure()

            val scanner = MediaScanner()
            val repository = (applicationContext as VindexApplication).photoRepository

            // Scan rapide (shallow)
            val photosFound = scanner.scanFolderShallow(applicationContext, folderUriString.toUri())

            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_scanning),
                    "PROGRESS" to 50
                )
            )
            delay(1000)

            // Synchronisation DB (ajouts/suppressions)
            repository.syncPhotos(photosFound)

            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_scanning),
                    "PROGRESS" to 100
                )
            )
            delay(1000)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}