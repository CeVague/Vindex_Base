package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.R
import com.cevague.vindex.ai.Assignment
import com.cevague.vindex.ai.FaceEngine
import com.cevague.vindex.ai.PersonCentroid
import com.cevague.vindex.ai.assignFace
import com.cevague.vindex.ai.weightedCentroid
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.search.asFloatArray
import com.cevague.vindex.search.toEmbeddingBlob
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class FaceAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsCache: SettingsCache,
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
                        val faceIds = personRepository.insertFaces(faces)
                        assignNewFaces(faceIds, analyzedFaces, active.embeddingDim)

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

    /**
     * Traduit en base la décision de `assignFace` pour chaque visage inséré.
     * [faceIds] et [analyzed] sont appariés **par position** — c'est le contrat
     * de `insertAll`.
     *
     * La liste des centroïdes est tenue en mémoire et **mutable** : une personne
     * créée pour le premier visage d'une photo doit être visible du deuxième.
     */
    private suspend fun assignNewFaces(
        faceIds: List<Long>,
        analyzed: List<FaceEngine.AnalyzedFace>,
        dim: Int
    ) {
        val high = settingsCache.faceThresholdHigh
        val medium = settingsCache.faceThresholdMedium

        val centroids = personRepository.getAllPersonsOnce()
            .mapNotNull { person ->
                person.centroidEmbedding?.let {
                    PersonCentroid(person.id, it.asFloatArray(dim), named = person.name != null)
                }
            }
            .toMutableList()

        // Seul un Auto fait bouger un centroïde : un Pending est une question, pas
        // une réponse, et corromprait la personne avant qu'on y ait répondu.
        val touched = mutableSetOf<Long>()

        for (i in faceIds.indices) {
            val embedding = analyzed[i].embedding


            when (val assignment = assignFace(embedding, centroids, high, medium)) {
                is Assignment.Auto -> {
                    personRepository.assignFaceToPerson(
                        faceId = faceIds[i],
                        personId = assignment.personId,
                        assignmentType = Face.ASSIGNMENT_AUTO,
                        confidence = assignment.similarity,
                        weight = assignment.similarity
                    )
                    touched += assignment.personId
                }

                is Assignment.Pending -> personRepository.assignFaceToPerson(
                    faceId = faceIds[i],
                    personId = assignment.personId,
                    assignmentType = Face.ASSIGNMENT_PENDING,
                    confidence = assignment.similarity,
                    weight = assignment.similarity
                )

                Assignment.NewPerson -> {
                    val personId = personRepository.createPerson()
                    personRepository.assignFaceToPerson(
                        faceId = faceIds[i],
                        personId = personId,
                        assignmentType = Face.ASSIGNMENT_AUTO,
                        confidence = 1f,
                        weight = 1f
                    )
                    // Moyenne d'un seul vecteur : lui-même, déjà normalisé L2.
                    personRepository.updateCentroid(personId, embedding.toEmbeddingBlob())
                    centroids += PersonCentroid(personId, embedding, named = false)
                }
            }
        }

        touched.forEach { recomputeCentroid(it, dim) }
    }

    /**
     * Recalcule le centroïde d'une personne depuis ses visages, plutôt que de
     * l'incrémenter : la moyenne est **pondérée** et le schéma ne stocke pas la
     * somme des poids. Seuls `auto` et `manual` y entrent.
     */
    private suspend fun recomputeCentroid(personId: Long, dim: Int) {
        val faces = personRepository.getFacesByPersonOnce(personId).filter {
            it.embedding != null &&
                    (it.assignmentType == Face.ASSIGNMENT_AUTO || it.assignmentType == Face.ASSIGNMENT_MANUAL)
        }
        if (faces.isEmpty()) return

        val centroid = weightedCentroid(
            faces.map { it.embedding!!.asFloatArray(dim) },
            faces.map { it.weight }
        )
        personRepository.updateCentroid(personId, centroid.toEmbeddingBlob())
    }
}