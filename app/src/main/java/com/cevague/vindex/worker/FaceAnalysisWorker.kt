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
import com.cevague.vindex.ai.FaceEngine
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.search.toEmbeddingBlob
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@HiltWorker
class FaceAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val personRepository: PersonRepository,
    private val photoRepository: PhotoRepository,
    private val faceEngine: FaceEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val active = faceEngine.activeFace() ?: return Result.success()
        val modelName = "${active.detectorModel}_${active.embedderModel}"
        val type = PhotoAnalysis.TYPE_FACES

        return try {
            val total = photoRepository.countPhotosMissingAnalysis(type, modelName)
            if (total == 0) return Result.success()

            reportProgress(0)
            var processed = 0

            while (true) {
                val batch = photoRepository.getPhotosMissingAnalysis(type, modelName, BATCH_SIZE)
                if (batch.isEmpty()) break

                val analyses = batch.map { photo ->
                    val startedAt = System.currentTimeMillis()
                    try {
                        val analyzedFaces = faceEngine.analyzeFaces(photo.filePath)
                        personRepository.deleteFacesByPhoto(photo.id)
                        val faces = analyzedFaces.map {
                            Face(
                                photoId = photo.id,
                                boxLeft = it.detected.boxLeft,
                                boxTop = it.detected.boxTop,
                                boxRight = it.detected.boxRight,
                                boxBottom = it.detected.boxBottom,
                                embedding = it.embedding.toEmbeddingBlob(),
                                embeddingModel = active.embedderModel,
                                confidence = it.detected.score
                            )
                        }
                        personRepository.insertFaces(faces)

                        PhotoAnalysis(
                            photoId = photo.id,
                            analysisType = type,
                            modelName = modelName,
                            textResult = faces.size.toString(),
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
                            modelName = modelName,
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
            Log.e(TAG, "Indexation des visages échouée", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            faceEngine.releaseSessions()
        }
    }

    private suspend fun reportProgress(percent: Int) {
        setProgress(
            workDataOf(
                "WORK" to applicationContext.getString(R.string.progress_faces),
                "PROGRESS" to percent
            )
        )
    }

    private companion object {
        const val TAG = "FaceAnalysisWorker"
        const val BATCH_SIZE = 10
    }
}