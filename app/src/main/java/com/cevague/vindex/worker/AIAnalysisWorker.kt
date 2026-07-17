package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Album
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException

@HiltWorker
class AIAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_generic),
                    "PROGRESS" to 0
                )
            )

            if (BuildConfig.DEBUG) {
                delay(1000)
                generateFakeAutoAlbums()

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_generic),
                        "PROGRESS" to 60
                    )
                )
                delay(1000)
            }

            Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Analyse IA échouée (IO)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: SQLiteException) {
            Log.e(TAG, "Analyse IA échouée (SQLite)", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Analyse IA échouée", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "AIAnalysisWorker"
    }

    /**
     * Stub debug : simule des albums « IA » en créant 1 à 2 albums auto avec des
     * photos aléatoires. Remplace les albums auto précédents à chaque scan.
     * Deviendra réel en phase 3+ (regroupement par événement/lieu).
     */
    private suspend fun generateFakeAutoAlbums() {
        val photos = photoRepository.getAllPhotosSummary().first()
        if (photos.size < 3) return

        albumRepository.deleteByType(Album.TYPE_AUTO_EVENT)

        val names = listOf(
            "Vacances à la mer", "Week-end en famille", "Sortie nature",
            "Soirée entre amis", "Balade en ville"
        )
        names.shuffled().take((1..2).random()).forEach { name ->
            val albumPhotos = photos.shuffled().take((3..8).random().coerceAtMost(photos.size))
            val albumId = albumRepository.insert(
                Album(
                    name = name,
                    albumType = Album.TYPE_AUTO_EVENT,
                    coverPhotoId = albumPhotos.first().id,
                    createdAt = System.currentTimeMillis()
                )
            )
            albumRepository.addPhotosToAlbum(albumId, albumPhotos.map { it.id })
        }
    }
}
