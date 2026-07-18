package com.cevague.vindex.search

import com.cevague.vindex.ai.ClipEngine
import com.cevague.vindex.ai.TranslationEngine
import com.cevague.vindex.data.database.dao.EmbeddingRow
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.CityRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.PhotoSearchCriteria
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * Pipeline de recherche hybride (ARCHITECTURE.md §6) :
 * requête → QueryParser → filtres durs SQL → candidats → scorer sémantique
 * CLIP sur les candidats → top-K par score.
 * Mode dégradé (aucun modèle CLIP actif) : le texte libre passe par un LIKE
 * nom de fichier / dossier et les candidats sont triés par date.
 */
@Singleton
class SearchPipeline @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val cityRepository: CityRepository,
    private val personRepository: PersonRepository,
    private val clipEngine: ClipEngine,
    private val translationEngine: TranslationEngine,
    private val settingsCache: SettingsCache
) {

    private val parser = QueryParser()

    data class Result(
        val parsed: ParsedQuery,
        val photos: List<PhotoSummary>,
        /** Similarités CLIP par photo (vide hors chemin sémantique) ; mode debug. */
        val scores: Map<Long, Float> = emptyMap(),
        /** Texte réellement encodé quand la requête a été traduite (mode debug). */
        val translatedQuery: String? = null,
        /**
         * La langue de requête n'est pas couverte par l'encodeur et aucun
         * traducteur ne l'a prise : l'UI affiche l'astuce du mode dégradé.
         */
        val translationMissing: Boolean = false
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
        removedPersonIds: Set<Long> = emptySet(),
        useCountryFilter: Boolean = true
    ): Result {
        // 1) Personnes d'abord, sur le texte BRUT : ensemble fermé de noms
        //    propres qu'un traducteur pourrait « traduire ».
        val personPass = parser.extractPersonsOnly(rawQuery, knownPersons())

        // 2) Traduction AVANT le parsing (décision utilisateur 2026-07-18) :
        //    même avec un encodeur multilingue, tout est ramené vers la langue
        //    du traducteur pour que les mots-clefs (dates, lieux, types) n'aient
        //    qu'UNE langue effective à gérer — l'anglais du lexique. Sans
        //    traducteur, le texte brut continue sur les lexiques fr+en, comme
        //    avant : rien ne casse, on parse juste dans la langue d'origine.
        val queryLanguage = queryLanguage()
        val translated = personPass.remainingText.takeIf { it.isNotBlank() }?.let {
            translationEngine.translate(it, queryLanguage)
        }
        val textToParse = translated ?: personPass.remainingText
        // L'astuce du mode dégradé ne vaut que si l'encodeur non plus ne
        // comprend pas la langue : lexiques fr + encodeur multilingue = rien
        // n'est perdu, on n'affiche rien.
        val translationMissing = translated == null &&
                clipEngine.activeClip()?.languages
                    ?.none { it.equals(queryLanguage, ignoreCase = true) } == true

        // 3) Parse du texte (traduit) : dates, type, villes, pays.
        val fullParsed = parser
            .parse(textToParse, knownCities(), emptyList(), knownCountries())
            .copy(persons = personPass.persons)
        val parsed = fullParsed.copy(
            dateRange = fullParsed.dateRange?.takeIf { useDateFilter },
            geoFilter = fullParsed.geoFilter?.takeIf { useGeoFilter },
            typeFilter = fullParsed.typeFilter?.takeIf { useTypeFilter },
            persons = fullParsed.persons.filterNot { it.personId in removedPersonIds },
            countryFilter = fullParsed.countryFilter?.takeIf { useCountryFilter }
        )

        return searchParsed(parsed).copy(
            translatedQuery = translated,
            translationMissing = translationMissing
        )
    }

    private suspend fun searchParsed(parsed: ParsedQuery): Result {
        val hasFilters = parsed.dateRange != null || parsed.geoFilter != null ||
                parsed.typeFilter != null || parsed.persons.isNotEmpty() ||
                parsed.countryFilter != null
        if (!hasFilters && parsed.freeText.trim().length < 2) {
            return Result(parsed, emptyList())
        }

        // Chemin sémantique : texte libre scoré par CLIP sur les candidats des
        // filtres durs (plus de LIKE ni de replis textuels dans ce mode).
        if (parsed.freeText.isNotBlank()) {
            val semantic = searchSemantic(parsed, hasFilters)
            if (semantic != null) {
                if (semantic.photos.isNotEmpty()) return semantic
                // Même arbitrage qu'en mode dégradé : un filtre ville/pays qui
                // vide les résultats rend son mot au texte (« nice sunset » sans
                // photo à Nice doit scorer « nice » comme adjectif, pas renvoyer
                // zéro). Seuls géo et pays sont ambigus — dates et personnes ne
                // sont jamais des mots de description.
                return semanticAmbiguityFallback(parsed) ?: semantic
            }
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

        // Même repli pour un pays qui vide les résultats (mot ambigu rendu au texte).
        if (parsed.countryFilter != null && !parsed.countryFilter.negated) {
            val fallback = parsed.copy(
                freeText = "${parsed.freeText} ${parsed.countryFilter.sourceText}".trim(),
                countryFilter = null
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

    suspend fun preload() {
        clipEngine.preload()
    }

    /**
     * Scorer sémantique (phase 2 §4.7, §4.9) : encode le texte libre, produit
     * scalaire par chunks sur les embeddings des candidats (ou de toute la
     * galerie sans filtre dur), top-K borné au-dessus du seuil, résultats
     * ordonnés par similarité. Renvoie null si aucun modèle CLIP actif
     * (mode dégradé → chemin LIKE).
     */
    private suspend fun searchSemantic(parsed: ParsedQuery, hasFilters: Boolean): Result? {
        val active = clipEngine.activeClip() ?: return null
        // Le texte libre arrive déjà traduit (cf. search) : on encode tel quel.
        val queryVector = clipEngine.encodeText(parsed.freeText) ?: return null

        val candidateIds: List<Long>? = if (hasFilters) {
            runFilters(parsed.copy(freeText = "")).map { it.id }
        } else {
            null // scan complet paginé
        }

        val topK = TopKCollector(MAX_SEMANTIC_RESULTS)
        val type = PhotoAnalysis.TYPE_CLIP_EMBEDDING
        // Seuil : ignoré en mode debug (« Afficher les scores » = tout voir),
        // sinon override manuel (Paramètres › IA), sinon le seuil du config.json
        // du modèle actif — les échelles varient par modèle (CLIP ~0.2, SigLIP ~0.06).
        val floor = when {
            settingsCache.showScores -> Float.NEGATIVE_INFINITY
            else -> settingsCache.searchThresholdOverride
                ?: active.similarityFloor
                ?: DEFAULT_SIMILARITY_FLOOR
        }

        fun score(rows: List<EmbeddingRow>) {
            for (row in rows) {
                if (row.embeddingDim != queryVector.size) continue
                val similarity =
                    dotProduct(queryVector, row.embedding.asFloatArray(row.embeddingDim))
                if (similarity >= floor) topK.offer(row.photoId, similarity)
            }
        }

        if (candidateIds != null) {
            if (candidateIds.isEmpty()) return Result(parsed, emptyList())
            // Chunk par chunk : accumuler tous les BLOBs d'un filtre large
            // (« 2024 » = des dizaines de milliers de photos) coûtait des dizaines
            // de Mo transitoires ; le TopK n'a besoin que d'un chunk à la fois.
            candidateIds.chunked(CANDIDATE_CHUNK_SIZE).forEach { chunk ->
                score(photoRepository.getEmbeddingsForPhotos(type, active.modelName, chunk))
            }
        } else {
            var afterPhotoId = 0L
            while (true) {
                val chunk = photoRepository.getEmbeddingsChunk(
                    type, active.modelName, afterPhotoId, SCAN_CHUNK_SIZE
                )
                if (chunk.isEmpty()) break
                score(chunk)
                afterPhotoId = chunk.last().photoId
            }
        }

        val ranked = topK.toSortedList()
        val orderedIds = ranked.map { it.id }
        return Result(
            parsed,
            photoRepository.getPhotosSummaryByIdsOrdered(orderedIds),
            ranked.associate { it.id to it.score }
        )
    }

    /** Langue des requêtes : le réglage, ou la locale de l'app (`system`). */
    private fun queryLanguage(): String {
        val setting = settingsCache.queryLanguage
        return if (setting == Setting.QUERY_LANGUAGE_SYSTEM) {
            Locale.getDefault().language
        } else {
            setting
        }
    }

    /**
     * Rejoue la recherche sémantique en rendant au texte libre le mot d'un filtre
     * ville puis pays (non niés) ; null si aucune variante ne produit de résultat.
     */
    private suspend fun semanticAmbiguityFallback(parsed: ParsedQuery): Result? {
        if (parsed.geoFilter != null && !parsed.geoFilter.negated) {
            val retry = parsed.copy(
                freeText = "${parsed.freeText} ${parsed.geoFilter.sourceText}".trim(),
                geoFilter = null
            )
            val stillFiltered = retry.dateRange != null || retry.typeFilter != null ||
                    retry.persons.isNotEmpty() || retry.countryFilter != null
            searchSemantic(retry, stillFiltered)
                ?.takeIf { it.photos.isNotEmpty() }
                ?.let { return it }
        }
        if (parsed.countryFilter != null && !parsed.countryFilter.negated) {
            val retry = parsed.copy(
                freeText = "${parsed.freeText} ${parsed.countryFilter.sourceText}".trim(),
                countryFilter = null
            )
            val stillFiltered = retry.dateRange != null || retry.geoFilter != null ||
                    retry.typeFilter != null || retry.persons.isNotEmpty()
            searchSemantic(retry, stillFiltered)
                ?.takeIf { it.photos.isNotEmpty() }
                ?.let { return it }
        }
        return null
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
                countryCode = parsed.countryFilter?.countryCode,
                countryNegated = parsed.countryFilter?.negated ?: false,
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
            cityRepository.getCityByNameAndCountry(name, countryCode)?.let { city ->
                // Les variantes suivent la ville : « Bombay » doit trouver Mumbai.
                KnownCity(
                    name = city.name,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    aliases = cityRepository.getAliasesForCity(city.id)
                )
            }
        }.distinct()

    /**
     * Pays de la galerie pour le parser : codes présents dans location_name
     * (« Nom, CC »), résolus en noms fr+en via java.util.Locale (multilingue,
     * zéro asset). Restreint aux pays réellement présents → pas de faux positifs.
     */
    private suspend fun knownCountries(): List<KnownCountry> {
        val displayLocales = listOf(Locale.FRENCH, Locale.ENGLISH)
        return photoRepository.getDistinctLocationNames()
            .mapNotNull { it.substringAfterLast(',', "").trim().takeIf { cc -> cc.length == 2 } }
            .distinct()
            .mapNotNull { code ->
                val countryLocale = runCatching {
                    Locale.Builder().setRegion(code).build()
                }.getOrNull() ?: return@mapNotNull null
                val names = displayLocales
                    .map { countryLocale.getDisplayCountry(it) }
                    .filter { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                    .distinct()
                if (names.isEmpty()) null else KnownCountry(code.uppercase(), names)
            }
    }

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
        val lonDelta =
            radiusKm / (KM_PER_DEGREE_LAT * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
        return BoundingBox(
            latitude - latDelta,
            latitude + latDelta,
            longitude - lonDelta,
            longitude + lonDelta
        )
    }

    private companion object {
        const val KM_PER_DEGREE_LAT = 111.0

        // Repli quand ni l'utilisateur ni le config.json du modèle ne fixent
        // de seuil (`similarity_floor`).
        const val DEFAULT_SIMILARITY_FLOOR = 0.2f
        const val MAX_SEMANTIC_RESULTS = 200
        const val SCAN_CHUNK_SIZE = 2000

        // Aligné sur la limite de variables SQLite (le repository chunke déjà à
        // 900) : un appel = une requête = un paquet borné en mémoire.
        const val CANDIDATE_CHUNK_SIZE = 900
    }
}
