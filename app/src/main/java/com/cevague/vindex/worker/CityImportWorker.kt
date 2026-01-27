package com.cevague.vindex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.local.FastSettings
import java.io.File
import java.io.FileOutputStream

class CityImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Vérifier si déjà importé via SharedPreferences pour plus de rapidité
            if (FastSettings.isCitiesLoaded) {
                return Result.success()
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

            // 1. Copier le fichier DB des assets vers un fichier temporaire
            val tempDbFile = File(applicationContext.cacheDir, "temp_cities.db")
            applicationContext.assets.open("cities15000.db").use { input ->
                FileOutputStream(tempDbFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Ouvrir la base de données temporaire
            val app = applicationContext as VindexApplication
            val db = app.database.openHelper.writableDatabase

            // On connecte le fichier temporaire
            db.execSQL("ATTACH DATABASE '${tempDbFile.absolutePath}' AS ext_db")

            try {
                // On copie tout d'un coup (C'est ici que le "streaming" se passe)
                db.execSQL(
                    "INSERT INTO cities (id, name, country_code, latitude, longitude, population) " +
                            "SELECT id, name, country_code, latitude, longitude, population FROM ext_db.cities15000"
                )
            } finally {
                // On libère le fichier quoi qu'il arrive
                db.execSQL("DETACH DATABASE ext_db")
            }

            tempDbFile.delete()

            FastSettings.isCitiesLoaded = true
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