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
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.search.asFloatArray
import com.cevague.vindex.search.dotProduct
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
     * Contrainte du domaine : **deux visages d'une même photo sont deux personnes
     * différentes**. Une personne servie sur cette photo sort donc des candidats
     * pour les visages suivants. Sans elle, deux frères sur une photo peuvent tous
     * deux dépasser le seuil pour le même groupe — et l'un des deux sera faux.
     */
    private suspend fun assignNewFaces(
        faceIds: List<Long>,
        analyzed: List<FaceEngine.AnalyzedFace>,
        dim: Int
    ) {
        // Écartés d'office : une main, un reflet, une silhouette floue d'arrière-plan
        // n'ont pas d'identité à disputer. Fait AVANT le mode manuel, sinon le tri à la
        // main hériterait de tout le rebut — c'est justement lui qu'on épargne.
        val floor = settingsCache.faceQualityFloor
        val kept = mutableListOf<Int>()
        for (i in faceIds.indices) {
            if (analyzed[i].quality < floor) {
                personRepository.markAsIgnored(faceIds[i], Face.EXCLUDED_LOW_QUALITY)
            } else {
                kept += i
            }
        }
        if (kept.isEmpty()) return

        if (!settingsCache.autoClusteringEnabled) {
            leaveForManualIdentification(kept.map { faceIds[it] })
            return
        }

        val high = settingsCache.faceThresholdHigh
        val medium = settingsCache.faceThresholdMedium

        val centroids = personRepository.getAllPersonsOnce().mapNotNull { person ->
            person.centroidEmbedding?.let {
                PersonCentroid(person.id, it.asFloatArray(dim), named = person.name != null)
            }
        }

        // Seul un Auto fait bouger un centroïde : un Pending est une question, pas
        // une réponse, et corromprait la personne avant qu'on y ait répondu.
        val touched = mutableSetOf<Long>()

        // Les personnes déjà servies par cette photo, y compris celles qu'on vient
        // de créer : elles ne peuvent pas réapparaître sur la même image.
        val takenInThisPhoto = mutableSetOf<Long>()

        for (i in kept) {
            val embedding = analyzed[i].embedding
            val quality = analyzed[i].quality

            logBestSimilarity(embedding, centroids, takenInThisPhoto)

            when (val assignment = assignFace(embedding, centroids, high, medium, takenInThisPhoto)) {
                is Assignment.Auto -> {
                    personRepository.assignFaceToPerson(
                        faceId = faceIds[i],
                        personId = assignment.personId,
                        assignmentType = Face.ASSIGNMENT_AUTO,
                        confidence = assignment.similarity,
                        weight = quality
                    )
                    touched += assignment.personId
                    takenInThisPhoto += assignment.personId
                }

                is Assignment.Pending -> {
                    personRepository.assignFaceToPerson(
                        faceId = faceIds[i],
                        personId = assignment.personId,
                        assignmentType = Face.ASSIGNMENT_PENDING,
                        confidence = assignment.similarity,
                        weight = quality
                    )
                    takenInThisPhoto += assignment.personId
                }

                Assignment.NewPerson -> {
                    val personId = personRepository.createPerson()
                    personRepository.assignFaceToPerson(
                        faceId = faceIds[i],
                        personId = personId,
                        assignmentType = Face.ASSIGNMENT_AUTO,
                        confidence = 1f,
                        weight = quality
                    )
                    // Moyenne d'un seul vecteur : lui-même, déjà normalisé L2.
                    personRepository.updateCentroid(personId, embedding.toEmbeddingBlob())
                    takenInThisPhoto += personId
                }
            }
        }

        touched.forEach { personRepository.recomputeCentroid(it) }
    }

    /**
     * Mode manuel (debug) : chaque visage part en attente, sans personne.
     *
     * Explicitement `pending`, et non laissé à `null` : c'est cette valeur que lit la
     * file d'identification (`assignment_type = 'pending'`), donc un visage inséré
     * sans elle n'apparaîtrait nulle part — analysé, et pourtant introuvable.
     */
    private suspend fun leaveForManualIdentification(faceIds: List<Long>) {
        faceIds.forEach { faceId ->
            personRepository.assignFaceToPerson(
                faceId = faceId,
                personId = null,
                assignmentType = Face.ASSIGNMENT_PENDING,
                confidence = null,
                weight = 1f
            )
        }
    }

    /**
     * Calibration de `high`/`medium` : la meilleure similarité **de chaque visage**,
     * y compris quand elle perd.
     *
     * C'est la seule donnée qui permette de placer les seuils, et aucune table ne la
     * conserve : `assignment_confidence` ne garde que celle des visages **retenus**,
     * or ce sont justement les perdants — les premières apparitions — qui bornent le
     * seuil par le bas. La frontière se lit dans le trou entre leur plafond et le
     * plancher des retrouvailles (mesuré à 0,353 / 0,453 le 2026-07-15).
     *
     * Recalculée ici plutôt qu'obtenue d'`assignFace` : la fonction pure renvoie une
     * **décision**, pas une mesure, et lui faire porter de quoi déboguer la
     * déformerait. Coût : k produits scalaires, en mode debug seulement.
     */
    private fun logBestSimilarity(
        embedding: FloatArray,
        centroids: List<PersonCentroid>,
        excluded: Set<Long>
    ) {
        if (!settingsCache.showScores) return
        val best = centroids
            .filterNot { it.personId in excluded }
            .maxOfOrNull { dotProduct(embedding, it.centroid) }
        Log.d(TAG, "CALIBRATION best=${best ?: "aucun candidat"} (high=${settingsCache.faceThresholdHigh} medium=${settingsCache.faceThresholdMedium})")
    }
}