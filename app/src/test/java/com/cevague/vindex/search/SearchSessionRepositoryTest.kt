package com.cevague.vindex.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSessionRepositoryTest {

    @Test
    fun `put puis get restitue la liste ordonnee`() {
        val repo = SearchSessionRepository()
        val id = repo.put(listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), repo.get(id))
    }

    @Test
    fun `get d'une session inconnue renvoie null`() {
        assertNull(SearchSessionRepository().get("inexistant"))
    }

    @Test
    fun `chaque put produit un id distinct`() {
        val repo = SearchSessionRepository()
        assertNotEquals(repo.put(listOf(1L)), repo.put(listOf(1L)))
    }

    @Test
    fun `LRU evince les sessions les plus anciennes au-dela de la limite`() {
        val repo = SearchSessionRepository()
        // MAX_SESSIONS = 4 : la 5e insertion évince la 1re.
        val ids = (1..5).map { repo.put(listOf(it.toLong())) }
        assertNull(repo.get(ids[0]))
        assertNotNull(repo.get(ids[4]))
    }
}
