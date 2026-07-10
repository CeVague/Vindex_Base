package com.cevague.vindex.data.repository

import android.content.Context
import com.cevague.vindex.data.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empreinte disque **propre à Vindex** (ce que l'app consomme en plus des photos
 * elles-mêmes) : les modèles importés et la base de données (métadonnées,
 * embeddings, visages, personnes, villes). Calcul volontairement simple :
 * taille des fichiers, pas de ventilation par table.
 */
@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Modèles importés (files/models/…). */
    fun modelsSize(): Long = File(context.filesDir, MODELS_DIR).dirSize()

    /** Fichier(s) de la base Room (analyses, visages, personnes, villes…). */
    fun databaseSize(): Long {
        val db = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        return listOf(db, File("${db.path}-wal"), File("${db.path}-shm"), File("${db.path}-journal"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    private fun File.dirSize(): Long =
        if (exists()) walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

    private companion object {
        const val MODELS_DIR = "models"
    }
}
