package com.cevague.vindex.search

import com.cevague.vindex.data.database.entity.Photo
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Analyse d'une requête de recherche (phase 2 §4.8) : extrait dates, type de
 * média, personnes et lieux vers des filtres durs, le reste devient le texte
 * libre du chemin sémantique. Chaque filtre peut être **nié** par un mot de
 * négation le précédant (« pas à Lille », « sans Alice », "not in 2026").
 *
 * Pur et synchrone : villes et personnes connues (celles de la galerie) sont
 * fournies par l'appelant, l'horloge et le fuseau sont injectables. Les motifs
 * de dates sont des lexiques par langue : ajouter une langue = ajouter un
 * lexique, l'algorithme ne change pas.
 */

data class DateRange(
    val startMs: Long,
    val endMs: Long, // inclus
    val negated: Boolean,
    val sourceText: String
)

data class GeoFilter(
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val cityName: String,
    val negated: Boolean,
    val sourceText: String
)

data class TypeFilter(
    val mediaType: Int, // Photo.MEDIA_TYPE_*
    val negated: Boolean,
    val sourceText: String
)

data class PersonFilter(
    val personId: Long,
    val personName: String,
    val negated: Boolean,
    val sourceText: String
)

data class CountryFilter(
    val countryCode: String,
    val negated: Boolean,
    val sourceText: String
)

/**
 * Ville présente dans la galerie. [name] est la forme **canonique** de GeoNames,
 * c'est-à-dire le nom international — « Vienna », pas « Wien » : c'est elle qui
 * étiquette le filtre. [aliases] sont ses **autres formes anglaises** en cours
 * (« Bombay » pour Mumbai, « Saigon » pour Ho Chi Minh City), qu'une requête
 * traduite peut tout aussi bien employer. Le matching porte sur les deux,
 * l'affichage sur le nom seul.
 */
data class KnownCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val aliases: List<String> = emptyList()
)

data class KnownPerson(val id: Long, val name: String)

/** Pays présent dans la galerie : code ISO (« FR ») + noms par langue pour le matching. */
data class KnownCountry(val code: String, val names: List<String>)

data class ParsedQuery(
    val freeText: String,
    val dateRange: DateRange? = null,
    val geoFilter: GeoFilter? = null,
    val typeFilter: TypeFilter? = null,
    val persons: List<PersonFilter> = emptyList(),
    val countryFilter: CountryFilter? = null
)

class QueryParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now() }
) {

    fun parse(
        query: String,
        knownCities: List<KnownCity> = emptyList(),
        knownPersons: List<KnownPerson> = emptyList(),
        knownCountries: List<KnownCountry> = emptyList()
    ): ParsedQuery {
        val tokens = tokenize(query).toMutableList()
        if (tokens.isEmpty()) return ParsedQuery("")

        val dateRange = extractDate(tokens)
        val typeFilter = extractType(tokens)
        val persons = extractPersons(tokens, knownPersons)
        // Ville (dérivée de la galerie) avant pays : plus spécifique.
        val geoFilter = extractCity(tokens, knownCities)
        val countryFilter = extractCountry(tokens, knownCountries)
        val freeText = tokens.joinToString(" ") { it.original }

        return ParsedQuery(freeText, dateRange, geoFilter, typeFilter, persons, countryFilter)
    }

    // ------------------------------------------------------------------ dates

    private fun extractDate(tokens: MutableList<Token>): DateRange? {
        val now = today()

        // Expressions relatives (motifs les plus longs d'abord)
        for ((phrase, resolver) in relativePhrases) {
            val start = indexOfPhrase(tokens, phrase)
            if (start >= 0) {
                val match = consume(tokens, start, phrase.size)
                val (from, to) = resolver(now)
                return toRange(from, to, match)
            }
        }

        for (i in tokens.indices) {
            val norm = tokens[i].normalized

            // Mois en toutes lettres + année : « août 2023 », "august 2023"
            val month = monthNames[norm]
            if (month != null && i + 1 < tokens.size) {
                val year = tokens[i + 1].normalized.toIntOrNull()
                if (year != null && year in 1900..2099) {
                    val match = consume(tokens, i, 2)
                    val ym = YearMonth.of(year, month)
                    return toRange(ym.atDay(1), ym.atEndOfMonth(), match)
                }
            }

            // MM/YYYY
            val mmYyyy = MM_YYYY.matchEntire(norm)
            if (mmYyyy != null) {
                val (mm, yyyy) = mmYyyy.destructured
                val ym = YearMonth.of(yyyy.toInt(), mm.toInt())
                val match = consume(tokens, i, 1)
                return toRange(ym.atDay(1), ym.atEndOfMonth(), match)
            }

            // Année seule
            if (YEAR.matches(norm)) {
                val year = norm.toInt()
                val match = consume(tokens, i, 1)
                return toRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), match)
            }
        }
        return null
    }

    private fun toRange(from: LocalDate, to: LocalDate, match: Consumed): DateRange {
        val startMs = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return DateRange(startMs, endMs, match.negated, match.source)
    }

    // ------------------------------------------------------------ type de média

    private fun extractType(tokens: MutableList<Token>): TypeFilter? {
        for ((phrase, mediaType) in mediaTypePhrases) {
            val start = indexOfPhrase(tokens, phrase)
            if (start >= 0) {
                val match = consume(tokens, start, phrase.size)
                return TypeFilter(mediaType, match.negated, match.source)
            }
        }
        return null
    }

    // -------------------------------------------------------------- personnes

    private fun extractPersons(
        tokens: MutableList<Token>,
        persons: List<KnownPerson>
    ): List<PersonFilter> {
        if (persons.isEmpty()) return emptyList()

        val indexed = persons
            .map { it to tokenize(it.name).map(Token::normalized) }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }

        // Prénom seul accepté si une seule personne le porte (« bob » →
        // « Bob Martin »), le nom complet reste prioritaire.
        val byFirstToken = indexed.groupBy { it.second.first() }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single().first }

        val found = mutableListOf<PersonFilter>()
        var matched = true
        while (matched) {
            matched = false
            for ((person, personTokens) in indexed) {
                if (found.any { it.personId == person.id }) continue
                val start = indexOfPhrase(tokens, personTokens)
                if (start >= 0) {
                    val match = consume(tokens, start, personTokens.size)
                    found += PersonFilter(person.id, person.name, match.negated, match.source)
                    matched = true
                    break
                }
            }
            if (matched) continue
            for (i in tokens.indices) {
                val person = byFirstToken[tokens[i].normalized] ?: continue
                if (found.any { it.personId == person.id }) continue
                val match = consume(tokens, i, 1)
                found += PersonFilter(person.id, person.name, match.negated, match.source)
                matched = true
                break
            }
        }
        return found
    }

    // ------------------------------------------------------------------ lieux

    private fun extractCity(tokens: MutableList<Token>, cities: List<KnownCity>): GeoFilter? {
        if (cities.isEmpty()) return null
        // Chaque ville est indexée par son nom ET ses exonymes, tokens normalisés,
        // les plus longs d'abord (« new york » avant « york ») : le matching est
        // ancré sur des tokens entiers, jamais un préfixe de mot. Trier par longueur
        // toutes formes confondues évite qu'un alias court d'une ville masque le nom
        // long d'une autre.
        val indexed = cities
            .flatMap { city -> (listOf(city.name) + city.aliases).map { city to tokenize(it).map(Token::normalized) } }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }

        for ((city, cityTokens) in indexed) {
            val start = indexOfPhrase(tokens, cityTokens)
            if (start >= 0) {
                val match = consume(tokens, start, cityTokens.size)
                return GeoFilter(
                    city.latitude, city.longitude, DEFAULT_RADIUS_KM,
                    city.name, match.negated, match.source
                )
            }
        }
        return null
    }

    // ------------------------------------------------------------------ pays

    /**
     * Pays de la galerie (« photos en France ») → filtre sur le code pays.
     * Comme les villes, la liste vient de l'appelant (codes présents dans les
     * photos, résolus en noms par langue) : le parser reste pur et le matching
     * est ancré sur des tokens entiers.
     */
    private fun extractCountry(
        tokens: MutableList<Token>,
        countries: List<KnownCountry>
    ): CountryFilter? {
        if (countries.isEmpty()) return null
        val indexed = countries
            .flatMap { country -> country.names.map { country.code to tokenize(it).map(Token::normalized) } }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }

        for ((code, nameTokens) in indexed) {
            val start = indexOfPhrase(tokens, nameTokens)
            if (start >= 0) {
                val match = consume(tokens, start, nameTokens.size)
                return CountryFilter(code, match.negated, match.source)
            }
        }
        return null
    }

    // ------------------------------------------------------------ tokenisation

    private data class Token(val original: String, val normalized: String)

    private data class Consumed(val source: String, val negated: Boolean)

    private fun tokenize(text: String): List<Token> =
        WORD.findAll(text).map { Token(it.value, normalize(it.value)) }.toList()

    private fun indexOfPhrase(tokens: List<Token>, phrase: List<String>): Int {
        outer@ for (i in 0..tokens.size - phrase.size) {
            for (j in phrase.indices) {
                if (tokens[i + j].normalized != phrase[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Retire [count] tokens à partir de [start], plus une éventuelle préposition
     * juste avant (« à Paris », "in august") puis un éventuel mot de négation
     * (« pas à Lille », « sans Alice ») ; renvoie le texte d'origine retiré et
     * l'état de négation.
     */
    private fun consume(tokens: MutableList<Token>, start: Int, count: Int): Consumed {
        var source = tokens.subList(start, start + count).joinToString(" ") { it.original }
        repeat(count) { tokens.removeAt(start) }
        var idx = start
        if (idx > 0 && tokens[idx - 1].normalized in prepositions) {
            idx--
            source = "${tokens[idx].original} $source"
            tokens.removeAt(idx)
        }
        var negated = false
        if (idx > 0 && tokens[idx - 1].normalized in negations) {
            idx--
            negated = true
            source = "${tokens[idx].original} $source"
            tokens.removeAt(idx)
        }
        return Consumed(source, negated)
    }

    companion object {
        const val DEFAULT_RADIUS_KM = 25.0

        private val WORD = Regex("[\\p{L}\\p{Nd}]+(?:/[\\p{Nd}]+)?")
        private val YEAR = Regex("(19|20)\\d{2}")
        private val MM_YYYY = Regex("(0?[1-9]|1[0-2])/((?:19|20)\\d{2})")

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .replace("œ", "oe")
                .replace("æ", "ae")

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        // --------------------------------------------------- lexiques (fr, en)

        private val prepositions = setOf(
            "a", "au", "aux", "en", "de", "du", "des", "pendant", "vers", "avec",
            "in", "on", "at", "during", "around", "near", "with"
        )

        private val negations = setOf(
            "pas", "sans", "non", "hors", "sauf",
            "not", "without", "no", "except", "excluding"
        )

        private val monthNames: Map<String, Int> = buildMap {
            listOf(
                "janvier", "fevrier", "mars", "avril", "mai", "juin",
                "juillet", "aout", "septembre", "octobre", "novembre", "decembre"
            ).forEachIndexed { i, name -> put(name, i + 1) }
            listOf(
                "january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"
            ).forEachIndexed { i, name -> put(name, i + 1) }
        }

        private val yesterday: (LocalDate) -> Pair<LocalDate, LocalDate> =
            { now -> now.minusDays(1) to now.minusDays(1) }
        private val todayRange: (LocalDate) -> Pair<LocalDate, LocalDate> =
            { now -> now to now }
        private val lastWeek: (LocalDate) -> Pair<LocalDate, LocalDate> = { now ->
            val start = now.with(DayOfWeek.MONDAY).minusWeeks(1)
            start to start.plusDays(6)
        }
        private val lastMonth: (LocalDate) -> Pair<LocalDate, LocalDate> = { now ->
            val ym = YearMonth.from(now).minusMonths(1)
            ym.atDay(1) to ym.atEndOfMonth()
        }
        private val lastYear: (LocalDate) -> Pair<LocalDate, LocalDate> = { now ->
            LocalDate.of(now.year - 1, 1, 1) to LocalDate.of(now.year - 1, 12, 31)
        }

        /** Dernière saison [startMonth]..[endMonth] entièrement écoulée. */
        private fun lastSeason(
            now: LocalDate,
            startMonth: Int,
            endMonth: Int
        ): Pair<LocalDate, LocalDate> {
            var year = now.year
            if (!YearMonth.of(year, endMonth).atEndOfMonth().isBefore(now)) year--
            return LocalDate.of(year, startMonth, 1) to YearMonth.of(year, endMonth).atEndOfMonth()
        }

        /** Dernier hiver écoulé (déc. année n-1 → fin février année n). */
        private fun lastWinter(now: LocalDate): Pair<LocalDate, LocalDate> {
            var year = now.year
            if (!YearMonth.of(year, 2).atEndOfMonth().isBefore(now)) year--
            return LocalDate.of(year - 1, 12, 1) to YearMonth.of(year, 2).atEndOfMonth()
        }

        private val lastSpring: (LocalDate) -> Pair<LocalDate, LocalDate> = { lastSeason(it, 3, 5) }
        private val lastSummer: (LocalDate) -> Pair<LocalDate, LocalDate> = { lastSeason(it, 6, 8) }
        private val lastAutumn: (LocalDate) -> Pair<LocalDate, LocalDate> =
            { lastSeason(it, 9, 11) }
        private val lastWinterR: (LocalDate) -> Pair<LocalDate, LocalDate> = { lastWinter(it) }

        /**
         * Expressions relatives, tokens normalisés (« l'été » → [l, ete]).
         * Triées des plus longues aux plus courtes pour que « la semaine
         * dernière » soit consommée avant que « semaine dernière » ne matche
         * en laissant traîner l'article.
         */
        private val relativePhrases: List<Pair<List<String>, (LocalDate) -> Pair<LocalDate, LocalDate>>> =
            listOf(
                "aujourd hui" to todayRange,
                "hier" to yesterday,
                "la semaine derniere" to lastWeek,
                "semaine derniere" to lastWeek,
                "le mois dernier" to lastMonth,
                "mois dernier" to lastMonth,
                "l annee derniere" to lastYear,
                "annee derniere" to lastYear,
                "l an dernier" to lastYear,
                "an dernier" to lastYear,
                "le printemps dernier" to lastSpring,
                "printemps dernier" to lastSpring,
                "l ete dernier" to lastSummer,
                "ete dernier" to lastSummer,
                "l automne dernier" to lastAutumn,
                "automne dernier" to lastAutumn,
                "l hiver dernier" to lastWinterR,
                "hiver dernier" to lastWinterR,
                "today" to todayRange,
                "yesterday" to yesterday,
                "last week" to lastWeek,
                "last month" to lastMonth,
                "last year" to lastYear,
                "last spring" to lastSpring,
                "last summer" to lastSummer,
                "last autumn" to lastAutumn,
                "last fall" to lastAutumn,
                "last winter" to lastWinterR
            )
                .map { (phrase, resolver) -> phrase.split(' ') to resolver }
                .sortedByDescending { it.first.size }

        /**
         * Types de média, tokens normalisés (« capture d'écran » → [capture, d,
         * ecran]). Motifs les plus longs d'abord.
         */
        private val mediaTypePhrases: List<Pair<List<String>, Int>> =
            listOf(
                "capture d ecran" to Photo.MEDIA_TYPE_SCREENSHOT,
                "captures d ecran" to Photo.MEDIA_TYPE_SCREENSHOT,
                "capture ecran" to Photo.MEDIA_TYPE_SCREENSHOT,
                "captures ecran" to Photo.MEDIA_TYPE_SCREENSHOT,
                "screenshot" to Photo.MEDIA_TYPE_SCREENSHOT,
                "screenshots" to Photo.MEDIA_TYPE_SCREENSHOT,
                "screen shot" to Photo.MEDIA_TYPE_SCREENSHOT,
                "screen shots" to Photo.MEDIA_TYPE_SCREENSHOT,
                "selfie" to Photo.MEDIA_TYPE_SELFIE,
                "selfies" to Photo.MEDIA_TYPE_SELFIE,
                "autoportrait" to Photo.MEDIA_TYPE_SELFIE,
                "autoportraits" to Photo.MEDIA_TYPE_SELFIE
            )
                .map { (phrase, type) -> phrase.split(' ') to type }
                .sortedByDescending { it.first.size }
    }
}
