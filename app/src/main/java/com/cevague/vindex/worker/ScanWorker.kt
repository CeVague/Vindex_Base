package com.cevague.vindex.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1. Récupérer l'URI du dossier à scanner (passée en paramètre au Worker)
        val folderUriString = inputData.getString("FOLDER_URI") ?: return@withContext Result.failure()
        val folderUri = folderUriString.toUri()

        try {
            // 2. Utiliser MediaScanner pour lister les photos
            val scanner = MediaScanner()
            val photosFound = scanner.scanFolder(applicationContext, folderUri)

            // 3. Récupérer la base de données via l'Application
            val photoDao = (applicationContext as VindexApplication).photoRepository

            // 4. Insérer les photos trouvées dans la base de données
            // On utilise une liste pour faire une insertion groupée (plus performant)
            if (photosFound.isNotEmpty()) {
                photoDao.insertAll(photosFound)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // Réessaie plus tard en cas d'erreur (ex: dossier inaccessible temporairement)
        }
    }
}
