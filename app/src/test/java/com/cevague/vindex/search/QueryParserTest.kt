package com.cevague.vindex.search

import com.cevague.vindex.data.database.entity.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class QueryParserTest {

    // 2026-07-06 est un lundi.
    private val today = LocalDate.of(2026, 7, 6)
    private val zone = ZoneId.of("UTC")
    private val parser = QueryParser(zone) { today }

    private val cities = listOf(
        KnownCity("Paris", 48.8566, 2.3522),
        KnownCity("Nice", 43.7102, 7.2620),
        KnownCity("Orléans", 47.9029, 1.9039),
        KnownCity("New York", 40.7128, -74.0060)
    )

    private fun startOf(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOf(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun assertRange(range: DateRange?, y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int) {
        assertNotNull(range)
        assertEquals(startOf(y1, m1, d1), range!!.startMs)
        assertEquals(endOf(y2, m2, d2), range.endMs)
    }

    // ------------------------------------------------------------- texte seul

    @Test
    fun `requete sans filtre reste texte libre`() {
        val parsed = parser.parse("chien sur la plage", cities)
        assertEquals("chien sur la plage", parsed.freeText)
        assertNull(parsed.dateRange)
        assertNull(parsed.geoFilter)
    }

    @Test
    fun `requete vide`() {
        val parsed = parser.parse("   ")
        assertEquals("", parsed.freeText)
        assertNull(parsed.dateRange)
        assertNull(parsed.geoFilter)
    }

    // ------------------------------------------------------------------ dates

    @Test
    fun `annee seule`() {
        val parsed = parser.parse("plage 2024")
        assertRange(parsed.dateRange, 2024, 1, 1, 2024, 12, 31)
        assertEquals("plage", parsed.freeText)
    }

    @Test
    fun `mois francais avec annee`() {
        val parsed = parser.parse("gâteau en août 2023")
        assertRange(parsed.dateRange, 2023, 8, 1, 2023, 8, 31)
        assertEquals("gâteau", parsed.freeText) // « en » consommé avec la date
    }

    @Test
    fun `mois anglais avec annee`() {
        val parsed = parser.parse("beach august 2023")
        assertRange(parsed.dateRange, 2023, 8, 1, 2023, 8, 31)
        assertEquals("beach", parsed.freeText)
    }

    @Test
    fun `format MM slash YYYY`() {
        val parsed = parser.parse("ski 02/2024")
        assertRange(parsed.dateRange, 2024, 2, 1, 2024, 2, 29)
        assertEquals("ski", parsed.freeText)
    }

    @Test
    fun `hier`() {
        val parsed = parser.parse("photos hier")
        assertRange(parsed.dateRange, 2026, 7, 5, 2026, 7, 5)
    }

    @Test
    fun `la semaine derniere`() {
        // Aujourd'hui lundi 6 → semaine précédente = lundi 29/06 au dimanche 05/07.
        val parsed = parser.parse("la semaine dernière")
        assertRange(parsed.dateRange, 2026, 6, 29, 2026, 7, 5)
        assertEquals("", parsed.freeText)
    }

    @Test
    fun `last week en anglais`() {
        val parsed = parser.parse("dog last week")
        assertRange(parsed.dateRange, 2026, 6, 29, 2026, 7, 5)
        assertEquals("dog", parsed.freeText)
    }

    @Test
    fun `le mois dernier`() {
        val parsed = parser.parse("le mois dernier")
        assertRange(parsed.dateRange, 2026, 6, 1, 2026, 6, 30)
    }

    @Test
    fun `l annee derniere`() {
        val parsed = parser.parse("montagne l'année dernière")
        assertRange(parsed.dateRange, 2025, 1, 1, 2025, 12, 31)
        assertEquals("montagne", parsed.freeText)
    }

    @Test
    fun `ete dernier en cours d ete renvoie l ete precedent`() {
        // Le 6 juillet 2026, l'été 2026 n'est pas terminé → été 2025.
        val parsed = parser.parse("l'été dernier")
        assertRange(parsed.dateRange, 2025, 6, 1, 2025, 8, 31)
    }

    @Test
    fun `hiver dernier chevauche deux annees`() {
        val parsed = parser.parse("neige l'hiver dernier")
        assertRange(parsed.dateRange, 2025, 12, 1, 2026, 2, 28)
        assertEquals("neige", parsed.freeText)
    }

    @Test
    fun `annee dans un nom de fichier n est pas une date de 5 chiffres`() {
        val parsed = parser.parse("IMG 20240")
        assertNull(parsed.dateRange)
    }

    // ------------------------------------------------------------------ lieux

    @Test
    fun `ville reconnue insensible aux accents et a la casse`() {
        val parsed = parser.parse("cathédrale orleans", cities)
        assertNotNull(parsed.geoFilter)
        assertEquals("Orléans", parsed.geoFilter!!.cityName)
        assertEquals(QueryParser.DEFAULT_RADIUS_KM, parsed.geoFilter!!.radiusKm, 0.0)
        assertEquals("cathédrale", parsed.freeText)
    }

    @Test
    fun `preposition avant la ville consommee`() {
        val parsed = parser.parse("chien à Paris", cities)
        assertEquals("Paris", parsed.geoFilter?.cityName)
        assertEquals("chien", parsed.freeText)
    }

    @Test
    fun `matching ancre - pas de prefixe de mot`() {
        val parsed = parser.parse("nicely done", cities)
        assertNull(parsed.geoFilter)
        assertEquals("nicely done", parsed.freeText)
    }

    @Test
    fun `ville multi-mots`() {
        val parsed = parser.parse("new york 2024", cities)
        assertEquals("New York", parsed.geoFilter?.cityName)
        assertRange(parsed.dateRange, 2024, 1, 1, 2024, 12, 31)
        assertEquals("", parsed.freeText)
    }

    @Test
    fun `ville inconnue reste dans le texte`() {
        val parsed = parser.parse("plage à Biarritz", cities)
        assertNull(parsed.geoFilter)
        assertEquals("plage à Biarritz", parsed.freeText)
    }

    @Test
    fun `sans liste de villes aucun filtre geo`() {
        val parsed = parser.parse("chien à Paris")
        assertNull(parsed.geoFilter)
    }

    // ---------------------------------------------------------- type de média

    @Test
    fun `capture d ecran en francais`() {
        val parsed = parser.parse("capture d'écran 2026")
        assertEquals(Photo.MEDIA_TYPE_SCREENSHOT, parsed.typeFilter?.mediaType)
        assertRange(parsed.dateRange, 2026, 1, 1, 2026, 12, 31)
        assertEquals("", parsed.freeText)
    }

    @Test
    fun `screenshots en anglais`() {
        val parsed = parser.parse("screenshots last week")
        assertEquals(Photo.MEDIA_TYPE_SCREENSHOT, parsed.typeFilter?.mediaType)
        assertNotNull(parsed.dateRange)
    }

    @Test
    fun `selfie avec ville`() {
        val parsed = parser.parse("selfies à Paris", cities)
        assertEquals(Photo.MEDIA_TYPE_SELFIE, parsed.typeFilter?.mediaType)
        assertEquals("Paris", parsed.geoFilter?.cityName)
        assertEquals("", parsed.freeText)
    }

    @Test
    fun `pas de type sans mot-cle`() {
        val parsed = parser.parse("chien sur la plage")
        assertNull(parsed.typeFilter)
    }

    // ----------------------------------------------------------------- combos

    @Test
    fun `date et lieu combines`() {
        val parsed = parser.parse("plage à nice en août 2023", cities)
        assertRange(parsed.dateRange, 2023, 8, 1, 2023, 8, 31)
        assertEquals("Nice", parsed.geoFilter?.cityName)
        assertEquals("plage", parsed.freeText)
    }

    @Test
    fun `sourceText conserve le texte d origine avec sa preposition`() {
        val parsed = parser.parse("plage à Nice en août 2023", cities)
        assertEquals("en août 2023", parsed.dateRange?.sourceText)
        assertEquals("à Nice", parsed.geoFilter?.sourceText)
    }

    // -------------------------------------------------------------- personnes

    private val persons = listOf(
        KnownPerson(1L, "Alice"),
        KnownPerson(2L, "Bob Martin"),
        KnownPerson(3L, "Marie")
    )

    @Test
    fun `personne par nom simple`() {
        val parsed = parser.parse("photos de alice", knownPersons = persons)
        assertEquals(listOf(1L), parsed.persons.map { it.personId })
        assertEquals(false, parsed.persons[0].negated)
        assertEquals("photos", parsed.freeText)
    }

    @Test
    fun `personne par prenom seul si unique`() {
        val parsed = parser.parse("bob 2024", knownPersons = persons)
        assertEquals(listOf(2L), parsed.persons.map { it.personId })
        assertEquals("Bob Martin", parsed.persons[0].personName)
        assertNotNull(parsed.dateRange)
    }

    @Test
    fun `plusieurs personnes`() {
        val parsed = parser.parse("alice avec marie", knownPersons = persons)
        assertEquals(setOf(1L, 3L), parsed.persons.map { it.personId }.toSet())
        assertEquals("", parsed.freeText)
    }

    @Test
    fun `personne inconnue reste dans le texte`() {
        val parsed = parser.parse("photos de charlie", knownPersons = persons)
        assertEquals(0, parsed.persons.size)
        assertEquals("photos de charlie", parsed.freeText)
    }

    // -------------------------------------------------------------- négations

    @Test
    fun `sans une personne`() {
        val parsed = parser.parse("plage sans alice", knownPersons = persons)
        assertEquals(true, parsed.persons[0].negated)
        assertEquals("sans alice", parsed.persons[0].sourceText)
        assertEquals("plage", parsed.freeText)
    }

    @Test
    fun `pas a une ville`() {
        val parsed = parser.parse("2026 pas à Lille", listOf(KnownCity("Lille", 50.63, 3.06)))
        assertEquals(true, parsed.geoFilter?.negated)
        assertEquals("pas à Lille", parsed.geoFilter?.sourceText)
        assertRange(parsed.dateRange, 2026, 1, 1, 2026, 12, 31)
        assertEquals(false, parsed.dateRange!!.negated)
    }

    @Test
    fun `date niee`() {
        val parsed = parser.parse("montagne pas en 2024")
        assertEquals(true, parsed.dateRange?.negated)
        assertEquals("montagne", parsed.freeText)
    }

    @Test
    fun `negation anglaise without`() {
        val parsed = parser.parse("beach without alice", knownPersons = persons)
        assertEquals(true, parsed.persons[0].negated)
    }

    @Test
    fun `negation non adjacente ignoree`() {
        val parsed = parser.parse("pas de chien à Paris", cities)
        // « pas » ne précède pas directement le filtre géo : Paris reste positif.
        assertEquals(false, parsed.geoFilter?.negated)
        assertEquals("pas de chien", parsed.freeText)
    }
}
