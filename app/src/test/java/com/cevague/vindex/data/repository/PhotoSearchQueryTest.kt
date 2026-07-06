package com.cevague.vindex.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSearchQueryTest {

    @Test
    fun `sans criteres - seulement is_hidden et tri`() {
        val (sql, args) = buildPhotoSearchQuery(PhotoSearchCriteria())
        assertTrue(sql.contains("is_hidden = 0"))
        assertTrue(sql.endsWith("ORDER BY date_taken DESC"))
        assertFalse(sql.contains("BETWEEN"))
        assertEquals(0, args.size)
    }

    @Test
    fun `filtre date positif et nie`() {
        val positive = buildPhotoSearchQuery(PhotoSearchCriteria(startMs = 1L, endMs = 2L))
        assertTrue(positive.first.contains("date_taken BETWEEN ? AND ?"))
        assertEquals(listOf<Any>(1L, 2L), positive.second)

        val negated = buildPhotoSearchQuery(
            PhotoSearchCriteria(startMs = 1L, endMs = 2L, dateNegated = true)
        )
        assertTrue(negated.first.contains("date_taken NOT BETWEEN ? AND ?"))
    }

    @Test
    fun `filtre type nie`() {
        val (sql, args) = buildPhotoSearchQuery(
            PhotoSearchCriteria(mediaType = 5, typeNegated = true)
        )
        assertTrue(sql.contains("media_type != ?"))
        assertEquals(listOf<Any>(5), args)
    }

    @Test
    fun `filtre geo nie inclut les photos sans GPS`() {
        val (sql, _) = buildPhotoSearchQuery(
            PhotoSearchCriteria(
                minLat = 1.0, maxLat = 2.0, minLon = 3.0, maxLon = 4.0, geoNegated = true
            )
        )
        assertTrue(sql.contains("AND NOT (latitude IS NOT NULL"))
    }

    @Test
    fun `personnes - EXISTS par personne, negation en NOT EXISTS`() {
        val (sql, args) = buildPhotoSearchQuery(
            PhotoSearchCriteria(
                persons = listOf(
                    PhotoSearchCriteria.PersonCriterion(7L, negated = false),
                    PhotoSearchCriteria.PersonCriterion(8L, negated = true)
                )
            )
        )
        assertTrue(sql.contains("AND EXISTS (SELECT 1 FROM faces"))
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM faces"))
        assertEquals(listOf<Any>(7L, 8L), args)
    }

    @Test
    fun `texte echappe et en dernier dans les arguments`() {
        val (sql, args) = buildPhotoSearchQuery(
            PhotoSearchCriteria(text = "50%", startMs = 1L, endMs = 2L)
        )
        assertTrue(sql.contains("file_name LIKE ? ESCAPE '\\'"))
        assertEquals(listOf<Any>(1L, 2L, "%50\\%%", "%50\\%%"), args)
    }
}
