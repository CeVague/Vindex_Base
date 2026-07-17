package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.FilePathAndSize
import com.cevague.vindex.data.database.entity.Photo

/**
 * Décisions pures du diffing de synchronisation MediaStore ↔ BDD.
 * Extraites de [PhotoRepository.syncPhotos] pour être testables sans Room ni
 * MediaStore. L'identité d'une photo est son URI content:// (colonne file_path),
 * stable au renommage/déplacement car dérivée de l'id MediaStore.
 */
internal object SyncDiff {

    /**
     * Photos d'un batch scanné à insérer ou mettre à jour : celles absentes de
     * la BDD, ou dont l'id MediaStore, la taille ou la date de modification a
     * changé. Les photos modifiées conservent leur URI donc leur id : l'upsert
     * met la ligne à jour en place, sans détruire les lignes filles (FK).
     */
    fun photosToUpsert(
        batch: List<Photo>,
        dbPhotosByUri: Map<String, FilePathAndSize>
    ): List<Photo> = batch.filter { scanned ->
        val existing = dbPhotosByUri[scanned.contentUri]
        existing == null ||
                existing.id != scanned.id ||
                existing.fileSize != scanned.fileSize ||
                existing.fileLastModified != scanned.fileLastModified
    }

    /**
     * URIs présentes en BDD mais absentes de l'énumération des fichiers vivants
     * des dossiers gérés : supprimées sur disque ou déplacées hors périmètre.
     */
    fun urisToDelete(
        dbUris: Set<String>,
        liveUris: Set<String>
    ): List<String> = (dbUris - liveUris).toList()

    /**
     * Photos du batch **déjà connues** dont le contenu a changé (taille ou date).
     * Leurs analyses IA et leurs visages sont périmés : l'embedding décrit
     * l'ancienne image et les boîtes sont normalisées sur elle — or la file
     * `NOT EXISTS` ne les recalculerait jamais tant que la ligne existe. C'est à
     * l'appelant de les supprimer pour que l'indexation incrémentale reprenne.
     */
    fun modifiedPhotoIds(
        batch: List<Photo>,
        dbPhotosByUri: Map<String, FilePathAndSize>
    ): List<Long> = batch.mapNotNull { scanned ->
        val existing = dbPhotosByUri[scanned.contentUri] ?: return@mapNotNull null
        scanned.id.takeIf {
            existing.fileSize != scanned.fileSize ||
                    existing.fileLastModified != scanned.fileLastModified
        }
    }
}
