package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class DiscoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val folderUriString =
                inputData.getString("FOLDER_URI") ?: return Result.failure()
            val folderUri = folderUriString.toUri()
            val repository = (applicationContext as VindexApplication).photoRepository

            repository.syncPhotos(applicationContext, folderUri) { count ->
                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_scanning),
                        "PROGRESS" to count
                    )
                )
            }
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