package com.cevague.vindex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeoNamesImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cityDao = (applicationContext as VindexApplication).database.cityDao()

            // Vérifier si déjà importé
            if (cityDao.getCount() > 0) {
                return@withContext Result.success()
            }

            setProgress(workDataOf("WORK" to "Import des villes...", "PROGRESS" to 0))

            val batch = mutableListOf<City>()
            var lineCount = 0
            val totalLines = 26000 // Approximatif pour la progression

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

                        // Insert par batch de 1000
                        if (batch.size >= 1000) {
                            cityDao.insertAll(batch.toList())
                            batch.clear()

                            lineCount += 1000
                            val progress = (lineCount * 100 / totalLines).coerceAtMost(99)
                            setProgress(
                                workDataOf(
                                    "WORK" to "Import des villes...",
                                    "PROGRESS" to progress
                                )
                            )
                        }
                    }
                }

                // Dernier batch
                if (batch.isNotEmpty()) {
                    cityDao.insertAll(batch)
                }
            }

            setProgress(workDataOf("WORK" to "Import des villes...", "PROGRESS" to 100))
            Result.success()

        } catch (e: Exception) {
            Result.failure()
        }
    }
}