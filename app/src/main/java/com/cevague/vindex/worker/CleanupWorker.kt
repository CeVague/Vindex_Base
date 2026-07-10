package com.cevague.vindex.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cevague.vindex.data.repository.PersonRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Nettoyage de fin de scan : recale les compteurs dénormalisés et supprime les
 * personnes devenues vides **non nommées** (après suppression de photos — édition
 * des dossiers indexés ou suppression depuis la galerie système). Les personnes
 * **nommées** sont conservées même vides (fausse manip, futur lien contact/notes).
 * Les cascades FK ayant déjà nettoyé embeddings/visages/albums, il ne reste que
 * ces agrégats à corriger. Idempotent.
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val personRepository: PersonRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            personRepository.recalculatePhotoCounts()
            personRepository.deleteEmptyUnnamed()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cleanup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "CleanupWorker"
    }
}
