package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.FilePathAndSize
import com.cevague.vindex.data.database.entity.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du diffing pur de synchronisation (items 1-3 de la dette phase 1) :
 * ajout, modification (id conservé), suppression, déplacement hors dossier.
 */
class SyncDiffTest {

    private fun uri(id: Long) = "content://media/external/images/media/$id"

    private fun scanned(
        id: Long,
        fileSize: Long = 1_000L,
        fileLastModified: Long = 100L
    ) = Photo(
        id = id,
        contentUri = uri(id),
        fileName = "IMG_$id.jpg",
        fileLastModified = fileLastModified,
        folderPath = "DCIM/Camera",
        dateAdded = 0L,
        fileSize = fileSize
    )

    private fun dbRow(
        id: Long,
        fileSize: Long = 1_000L,
        fileLastModified: Long = 100L
    ) = FilePathAndSize(
        id = id,
        filePath = uri(id),
        fileSize = fileSize,
        fileLastModified = fileLastModified
    )

    // --- photosToUpsert ---

    @Test
    fun `nouvelle photo absente de la BDD est upsertee`() {
        val batch = listOf(scanned(1))
        val result = SyncDiff.photosToUpsert(batch, emptyMap())
        assertEquals(listOf(scanned(1)), result)
    }

    @Test
    fun `photo inchangee est ignoree`() {
        val db = mapOf(uri(1) to dbRow(1))
        val result = SyncDiff.photosToUpsert(listOf(scanned(1)), db)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `photo modifiee (taille) est upsertee en conservant son id`() {
        val db = mapOf(uri(1) to dbRow(1, fileSize = 1_000L))
        val result = SyncDiff.photosToUpsert(listOf(scanned(1, fileSize = 2_000L)), db)
        assertEquals(1, result.size)
        // Même URI ⇒ même id ⇒ l'upsert met à jour la ligne existante (pas d'insertion neuve).
        assertEquals(1L, result.single().id)
        assertEquals(2_000L, result.single().fileSize)
    }

    @Test
    fun `photo modifiee (date) est upsertee`() {
        val db = mapOf(uri(1) to dbRow(1, fileLastModified = 100L))
        val result = SyncDiff.photosToUpsert(listOf(scanned(1, fileLastModified = 200L)), db)
        assertEquals(1, result.size)
    }

    @Test
    fun `id MediaStore change pour une meme URI declenche un upsert`() {
        // Cas rare : purge MediaStore. La map est indexée par URI ; un id différent
        // pour la même clé doit être détecté comme changement.
        val db = mapOf(uri(1) to dbRow(id = 42, fileSize = 1_000L, fileLastModified = 100L))
        val result = SyncDiff.photosToUpsert(listOf(scanned(1)), db)
        assertEquals(1, result.size)
    }

    @Test
    fun `batch mixte ne retient que nouvelles et modifiees`() {
        val db = mapOf(
            uri(1) to dbRow(1),                       // inchangée
            uri(2) to dbRow(2, fileSize = 1_000L)     // modifiée
        )
        val batch = listOf(
            scanned(1),                                // inchangée -> exclue
            scanned(2, fileSize = 5_000L),             // modifiée -> incluse
            scanned(3)                                 // nouvelle -> incluse
        )
        val result = SyncDiff.photosToUpsert(batch, db).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), result)
    }

    @Test
    fun `batch vide donne liste vide`() {
        assertTrue(SyncDiff.photosToUpsert(emptyList(), mapOf(uri(1) to dbRow(1))).isEmpty())
    }

    // --- modifiedPhotoIds ---

    @Test
    fun `photo modifiee est signalee pour invalidation de ses analyses`() {
        val db = mapOf(uri(1) to dbRow(1, fileSize = 1_000L))
        val result = SyncDiff.modifiedPhotoIds(listOf(scanned(1, fileSize = 2_000L)), db)
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `nouvelle photo n est pas signalee comme modifiee`() {
        // Une nouvelle photo n'a rien à invalider : elle n'a encore aucune analyse.
        val result = SyncDiff.modifiedPhotoIds(listOf(scanned(1)), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `photo inchangee n est pas signalee comme modifiee`() {
        val db = mapOf(uri(1) to dbRow(1))
        assertTrue(SyncDiff.modifiedPhotoIds(listOf(scanned(1)), db).isEmpty())
    }

    @Test
    fun `batch mixte ne signale que les modifiees`() {
        val db = mapOf(
            uri(1) to dbRow(1),                        // inchangée
            uri(2) to dbRow(2, fileLastModified = 50L) // modifiée (date)
        )
        val batch = listOf(scanned(1), scanned(2), scanned(3))
        assertEquals(listOf(2L), SyncDiff.modifiedPhotoIds(batch, db))
    }

    // --- urisToDelete ---

    @Test
    fun `photo supprimee du disque est marquee pour suppression`() {
        val db = setOf(uri(1), uri(2), uri(3))
        val live = setOf(uri(1), uri(2))            // 3 a disparu
        assertEquals(listOf(uri(3)), SyncDiff.urisToDelete(db, live))
    }

    @Test
    fun `photo deplacee hors dossier gere est marquee pour suppression`() {
        // Déplacée hors périmètre : absente de l'énumération des dossiers gérés.
        val db = setOf(uri(1), uri(2))
        val live = setOf(uri(1))
        assertEquals(listOf(uri(2)), SyncDiff.urisToDelete(db, live))
    }

    @Test
    fun `aucune suppression quand tout est vivant`() {
        val db = setOf(uri(1), uri(2))
        assertTrue(SyncDiff.urisToDelete(db, setOf(uri(1), uri(2))).isEmpty())
    }

    @Test
    fun `BDD vide ne supprime rien`() {
        assertTrue(SyncDiff.urisToDelete(emptySet(), setOf(uri(1))).isEmpty())
    }
}
