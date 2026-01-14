package com.cevague.vindex.worker

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Setting
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
            val folderUriString = inputData.getString("FOLDER_URI") ?: return@withContext Result.failure()
            val folderUri = folderUriString.toUri()
            val repository = (applicationContext as VindexApplication).photoRepository

            repository.syncPhotos(applicationContext, folderUri) { count ->
                setProgress(workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_scanning),
                    "PROGRESS" to count
                ))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}