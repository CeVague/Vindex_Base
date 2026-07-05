package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.PhotoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Réordonnancement des résultats selon l'ordre de la session (item 5) : SQLite
 * `IN (...)` ne préserve pas l'ordre des ids, on le rétablit côté application.
 */
class ReorderByIdsTest {

    private fun summary(id: Long) =
        PhotoSummary(id = id, filePath = "uri$id", fileName = "n$id.jpg", dateAdded = 0L, dateTaken = null, isFavorite = false)

    @Test
    fun `l'ordre des ids est preserve independamment de l'ordre charge`() {
        val loadedInDbOrder = listOf(summary(1), summary(2), summary(3))
        val result = reorderByIds(listOf(3L, 1L, 2L), loadedInDbOrder).map { it.id }
        assertEquals(listOf(3L, 1L, 2L), result)
    }

    @Test
    fun `un id sans photo correspondante est ignore`() {
        val result = reorderByIds(listOf(9L, 1L), listOf(summary(1))).map { it.id }
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `liste d'ids vide donne liste vide`() {
        assertTrue(reorderByIds(emptyList(), listOf(summary(1))).isEmpty())
    }
}
