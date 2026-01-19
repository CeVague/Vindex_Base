package com.cevague.vindex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.City
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CityImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Vérifier si déjà importé via SharedPreferences pour plus de rapidité
            if (FastSettings.isCitiesLoaded) {
                return@withContext Result.success()
            }

            val repository = (applicationContext as VindexApplication).cityRepository

            // Supprimer les anciennes données
            repository.deleteAll()

            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_loading_cities),
                    "PROGRESS" to 0
                )
            )

            val batch = mutableListOf<City>()
            var lineCount = 0
            val totalLines = 33000 // Approximatif pour la progression
            val batch_size = 1000

            applicationContext.assets.open("cities15000.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val cols = line.split("\t")
                    if (cols.size >= 15) {
                        batch.add(
                            City(
                                id = cols[0].toLongOrNull() ?: return@forEach,
                                name = cols[1],
                                countryCode = cols[8],
                                latitude = cols[4].toDoubleOrNull() ?: return@forEach,
                                longitude = cols[5].toDoubleOrNull() ?: return@forEach,
                                population = cols[14].toIntOrNull() ?: 0
                            )
                        )

                        // Insert par batch
                        if (batch.size >= batch_size) {
                            repository.insertAll(batch.toList())
                            batch.clear()

                            lineCount += batch_size
                            val progress = (lineCount * 100 / totalLines).coerceAtMost(99)
                            setProgress(
                                workDataOf(
                                    "WORK" to applicationContext.getString(R.string.progress_loading_cities),
                                    "PROGRESS" to progress
                                )
                            )
                        }
                    }
                }

                // Dernier batch
                if (batch.isNotEmpty()) {
                    repository.insertAll(batch)
                }
            }

            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_loading_cities),
                    "PROGRESS" to 100
                )
            )
            
            Result.success()

        } catch (e: Exception) {
            Result.failure()
        }
    }
}
