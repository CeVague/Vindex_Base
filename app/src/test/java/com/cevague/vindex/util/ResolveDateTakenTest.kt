package com.cevague.vindex.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Repli de la date de prise de vue vers la date de modification du fichier
 * (item 6) : EXIF absent (null), nul ou négatif → date de modification.
 */
class ResolveDateTakenTest {

    private val modified = 1_700_000_000_000L

    @Test
    fun `date EXIF valide est conservee`() {
        assertEquals(1_650_000_000_000L, resolveDateTaken(1_650_000_000_000L, modified))
    }

    @Test
    fun `date EXIF nulle replie sur la date de modification`() {
        assertEquals(modified, resolveDateTaken(null, modified))
    }

    @Test
    fun `date EXIF a zero (screenshot sans EXIF) replie sur la date de modification`() {
        assertEquals(modified, resolveDateTaken(0L, modified))
    }

    @Test
    fun `date EXIF negative replie sur la date de modification`() {
        assertEquals(modified, resolveDateTaken(-1L, modified))
    }
}
