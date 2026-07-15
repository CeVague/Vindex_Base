package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.ai.ClipEngine
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.search.toEmbeddingBlob
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Indexation CLIP (phase 2 §4.4) : encode les photos sans embedding pour le
 * modèle actif. Reprise incrémentale par requête « photos sans ligne
 * photo_analyses » — un échec par photo est enregistré (embedding NULL) pour
 * ne pas rester en file indéfiniment. Sort immédiatement si aucun modèle actif.
 */
@HiltWorker
class ClipIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository,
    private val clipEngine: ClipEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val active = clipEngine.activeClip() ?: return Result.success()
        val type = PhotoAnalysis.TYPE_CLIP_EMBEDDING

        return try {
            val total = photoRepository.countPhotosMissingAnalysis(type, active.modelName)
            if (total == 0) return Result.success()

            reportProgress(0)
            var processed = 0

            while (true) {
                val batch =
                    photoRepository.getPhotosMissingAnalysis(type, active.modelName, BATCH_SIZE)
                if (batch.isEmpty()) break

                val analyses = batch.map { photo ->
                    val startedAt = System.currentTimeMillis()
                    try {
                        val embedding = clipEngine.encodeImage(photo.filePath)
                            ?: return Result.success() // modèle désactivé en cours de route
                        PhotoAnalysis(
                            photoId = photo.id,
                            analysisType = type,
                            modelName = active.modelName,
                            embedding = embedding.toEmbeddingBlob(),
                            embeddingDim = embedding.size,
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Photo illisible/corrompue : échec enregistré, on continue.
                        Log.e(TAG, "Encodage impossible pour photo ${photo.id}", e)
                        PhotoAnalysis(
                            photoId = photo.id,
                            analysisType = type,
                            modelName = active.modelName,
                            textResult = "error: ${e.message?.take(200)}",
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                    }
                }

                photoRepository.upsertAnalyses(analyses)
                processed += batch.size
                reportProgress((processed * 100 / total).coerceAtMost(100))
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Indexation CLIP échouée", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            // L'encodeur image mappe plusieurs centaines de Mo : libéré hors indexation.
            clipEngine.releaseSessions()
        }
    }

    private suspend fun reportProgress(percent: Int) {
        setProgress(
            workDataOf(
                "WORK" to applicationContext.getString(R.string.progress_vector),
                "PROGRESS" to percent
            )
        )
    }

    private companion object {
        const val TAG = "ClipIndexWorker"

        // Petits batchs : la progression avance visiblement même sur une
        // galerie modeste (l'écriture BDD par batch reste peu coûteuse).
        const val BATCH_SIZE = 10
    }
}
