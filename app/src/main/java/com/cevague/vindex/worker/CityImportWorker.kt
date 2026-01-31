package com.cevague.vindex.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.CityRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class CityImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cityRepository: CityRepository,
    private val database: AppDatabase,
    private val settingsCache: SettingsCache
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Vérifier si déjà importé via SharedPreferences pour plus de rapidité
            if (settingsCache.isCitiesLoaded) {
                return Result.success()
            }

            // Supprimer les anciennes données
            cityRepository.deleteAll()

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
            val db = database.openHelper.writableDatabase

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

            settingsCache.isCitiesLoaded = true
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