package com.cevague.vindex.search

import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.repository.CityRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.PhotoSearchCriteria
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * Pipeline de recherche hybride (ARCHITECTURE.md §6) :
 * requête → QueryParser → filtres durs SQL → candidats → [scorers, phase 2+].
 * v1 : pas encore de scorer sémantique, le texte libre passe par le filtre
 * nom de fichier / dossier et les candidats sont triés par date.
 */
@Singleton
class SearchPipeline @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val cityRepository: CityRepository,
    private val personRepository: PersonRepository
) {

    private val parser = QueryParser()

    data class Result(
        val parsed: ParsedQuery,
        val photos: List<PhotoSummary>
    )

    /**
     * Les paramètres `use*` / [removedPersonIds] désactivent un filtre retiré
     * par l'utilisateur (chip fermée) : la contrainte disparaît, ses mots ne
     * reviennent pas au texte.
     */
    suspend fun search(
        rawQuery: String,
        useDateFilter: Boolean = true,
        useGeoFilter: Boolean = true,
        useTypeFilter: Boolean = true,
        removedPersonIds: Set<Long> = emptySet()
    ): Result {
        val fullParsed = parser.parse(rawQuery, knownCities(), knownPersons())
        val parsed = fullParsed.copy(
            dateRange = fullParsed.dateRange?.takeIf { useDateFilter },
            geoFilter = fullParsed.geoFilter?.takeIf { useGeoFilter },
            typeFilter = fullParsed.typeFilter?.takeIf { useTypeFilter },
            persons = fullParsed.persons.filterNot { it.personId in removedPersonIds }
        )

        val hasFilters = parsed.dateRange != null || parsed.geoFilter != null ||
            parsed.typeFilter != null || parsed.persons.isNotEmpty()
        if (!hasFilters && parsed.freeText.trim().length < 2) {
            return Result(parsed, emptyList())
        }

        val photos = runFilters(parsed)
        if (photos.isNotEmpty()) return Result(parsed, photos)

        // Ambiguïté résolue en faveur du texte libre : si le filtre géo vide les
        // résultats (« Nice » adjectif…), le mot retourne dans le texte.
        if (parsed.geoFilter != null && !parsed.geoFilter.negated) {
            val fallback = parsed.copy(
                freeText = "${parsed.freeText} ${parsed.geoFilter.sourceText}".trim(),
                geoFilter = null
            )
            val fallbackPhotos = runFilters(fallback)
            if (fallbackPhotos.isNotEmpty()) return Result(fallback, fallbackPhotos)
        }

        // Tant qu'il n'y a pas de scorer sémantique, le texte libre n'est qu'un
        // LIKE nom de fichier/dossier : s'il vide des résultats que les filtres
        // durs trouvaient (« plage 2026 »), on sert les filtres seuls plutôt que
        // rien. Le scorer CLIP remplacera ce repli.
        if (parsed.freeText.isNotBlank() && hasFilters) {
            val filtersOnly = parsed.copy(freeText = "")
            val fallbackPhotos = runFilters(filtersOnly)
            if (fallbackPhotos.isNotEmpty()) return Result(filtersOnly, fallbackPhotos)
        }

        return Result(parsed, photos)
    }

    private suspend fun runFilters(parsed: ParsedQuery): List<PhotoSummary> {
        val box = parsed.geoFilter?.toBoundingBox()
        return photoRepository.searchFiltered(
            PhotoSearchCriteria(
                text = parsed.freeText.takeIf { it.isNotBlank() },
                startMs = parsed.dateRange?.startMs,
                endMs = parsed.dateRange?.endMs,
                dateNegated = parsed.dateRange?.negated ?: false,
                mediaType = parsed.typeFilter?.mediaType,
                typeNegated = parsed.typeFilter?.negated ?: false,
                minLat = box?.minLat,
                maxLat = box?.maxLat,
                minLon = box?.minLon,
                maxLon = box?.maxLon,
                geoNegated = parsed.geoFilter?.negated ?: false,
                persons = parsed.persons.map {
                    PhotoSearchCriteria.PersonCriterion(it.personId, it.negated)
                }
            )
        )
    }

    /**
     * Villes de la galerie pour le parser : location_name distincts (« Nom, CC »)
     * résolus en coordonnées via la table cities.
     */
    private suspend fun knownCities(): List<KnownCity> =
        photoRepository.getDistinctLocationNames().mapNotNull { location ->
            val name = location.substringBefore(',').trim()
            val countryCode = location.substringAfter(',', "").trim()
            if (name.isEmpty() || countryCode.isEmpty()) return@mapNotNull null
            cityRepository.getCityByNameAndCountry(name, countryCode)
                ?.let { KnownCity(it.name, it.latitude, it.longitude) }
        }.distinct()

    /** Personnes nommées de la galerie pour le parser. */
    private suspend fun knownPersons(): List<KnownPerson> =
        personRepository.getAllPersonSummaryOnce()
            .mapNotNull { person -> person.name?.let { KnownPerson(person.id, it) } }

    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private fun GeoFilter.toBoundingBox(): BoundingBox {
        val latDelta = radiusKm / KM_PER_DEGREE_LAT
        val lonDelta = radiusKm / (KM_PER_DEGREE_LAT * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
        return BoundingBox(latitude - latDelta, latitude + latDelta, longitude - lonDelta, longitude + lonDelta)
    }

    private companion object {
        const val KM_PER_DEGREE_LAT = 111.0
    }
}
