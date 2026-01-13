package com.cevague.vindex.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AIAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        try {
            setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_generic), "PROGRESS" to 0))

            // Estimation d'un batch selon le nombre de coeurs
            val cores = Runtime.getRuntime().availableProcessors()
            val batchSize = (cores * 5).coerceIn(5, 50)

            // Ajustement du batch pour les appareils Low RAM
            val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isLowRamDevice = activityManager.isLowRamDevice
            // Si c'est un appareil Low RAM, on divise le batch par 2
            val finalBatchSize = if (isLowRamDevice) (batchSize / 2).coerceAtLeast(5) else batchSize

            delay(1000)

            setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_generic), "PROGRESS" to 42))

            delay(1000)

            setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_generic), "PROGRESS" to 68))

            delay(1000)

            setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_generic), "PROGRESS" to 89))

            delay(1000)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}