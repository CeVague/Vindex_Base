package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.R
import kotlinx.coroutines.delay
import java.io.IOException

class AIAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_generic),
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
                delay(1000)

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_generic),
                        "PROGRESS" to 36
                    )
                )

                delay(1000)

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_generic),
                        "PROGRESS" to 58
                    )
                )

                delay(1000)

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_generic),
                        "PROGRESS" to 89
                    )
                )

                delay(1000)
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