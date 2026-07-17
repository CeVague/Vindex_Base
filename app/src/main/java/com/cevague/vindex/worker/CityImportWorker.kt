package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream

/**
 * Recopie l'asset des villes (GeoNames, construit par `tools/build_cities_db.py`)
 * dans Room au premier lancement : villes pour le reverse geocoding, exonymes pour
 * le matching des requêtes.
 *
 * L'asset reste dans l'APK après la copie — la donnée existe donc en double, sur
 * disque et dans l'APK. C'est le prix d'une base pré-calculée sans téléchargement.
 */
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
            // Comparé à une version explicite et non à un simple booléen : cf.
            // SettingsCache.citiesAssetLoaded — les préférences survivent à une
            // migration destructive, un booléen ferait sauter l'import à jamais.
            // Le COUNT ferme le cas restant : migration destructive SANS changement
            // d'asset (les drapeaux restent vrais sur une table vidée, et la
            // recherche géo mourrait en silence).
            if (settingsCache.citiesAssetLoaded == CITIES_VERSION &&
                settingsCache.isCitiesLoaded &&
                cityRepository.getCount() > 0
            ) {
                return Result.success()
            }

            cityRepository.deleteAll()
            reportProgress(0)

            val tempDbFile = File(applicationContext.cacheDir, "temp_cities.db")
            applicationContext.assets.open(CITIES_ASSET).use { input ->
                FileOutputStream(tempDbFile).use { output -> input.copyTo(output) }
            }

            val db = database.openHelper.writableDatabase
            db.execSQL("ATTACH DATABASE '${tempDbFile.absolutePath}' AS ext_db")
            try {
                db.execSQL(
                    "INSERT INTO cities (id, name, country_code, latitude, longitude, population) " +
                            "SELECT id, name, country_code, latitude, longitude, population FROM ext_db.cities"
                )
                // Après les villes : la FK des alias pointe dessus.
                db.execSQL(
                    "INSERT INTO city_aliases (city_id, alias) " +
                            "SELECT city_id, alias FROM ext_db.city_aliases"
                )
            } finally {
                db.execSQL("DETACH DATABASE ext_db")
            }

            tempDbFile.delete()

            settingsCache.isCitiesLoaded = true
            settingsCache.citiesAssetLoaded = CITIES_VERSION
            reportProgress(100)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Import des villes échoué", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun reportProgress(percent: Int) {
        setProgress(
            workDataOf(
                "WORK" to applicationContext.getString(R.string.progress_loading_cities),
                "PROGRESS" to percent
            )
        )
    }

    private companion object {
        const val TAG = "CityImportWorker"

        /** Nom volontairement générique : la base peut changer de densité sans renommage. */
        const val CITIES_ASSET = "cities.db"

        /**
         * Version du contenu de l'asset, à incrémenter **à chaque régénération**.
         *
         * Le nom du fichier étant générique, il ne peut plus servir de marqueur : sans
         * cette constante, remplacer l'asset laisserait les installations existantes
         * sur l'ancienne table, en silence. Décrit ce qui a été construit — densité,
         * langue des alias — pour que la valeur se relise.
         */
        const val CITIES_VERSION = "cities5000-en-v1"
    }
}
