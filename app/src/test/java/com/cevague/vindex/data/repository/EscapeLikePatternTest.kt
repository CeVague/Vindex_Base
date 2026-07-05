package com.cevague.vindex.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** Échappement des métacaractères LIKE d'une saisie utilisateur (item 10). */
class EscapeLikePatternTest {

    @Test
    fun `pourcent est echappe`() {
        assertEquals("50\\% de remise", "50% de remise".escapeLikePattern())
    }

    @Test
    fun `underscore est echappe`() {
        assertEquals("IMG\\_001", "IMG_001".escapeLikePattern())
    }

    @Test
    fun `backslash est echappe en premier`() {
        assertEquals("a\\\\b", "a\\b".escapeLikePattern())
    }

    @Test
    fun `texte normal reste inchange`() {
        assertEquals("vacances", "vacances".escapeLikePattern())
    }
}
