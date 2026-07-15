package com.cevague.vindex.data.repository

/**
 * Critères des filtres durs de la recherche (ARCHITECTURE.md §6), neutres
 * vis-à-vis du package `search/` (la couche data ne dépend pas de lui).
 * Chaque filtre est optionnel et peut être nié.
 */
data class PhotoSearchCriteria(
    val text: String? = null, // texte brut ; échappé par le builder
    val startMs: Long? = null,
    val endMs: Long? = null,
    val dateNegated: Boolean = false,
    val mediaType: Int? = null,
    val typeNegated: Boolean = false,
    val minLat: Double? = null,
    val maxLat: Double? = null,
    val minLon: Double? = null,
    val maxLon: Double? = null,
    val geoNegated: Boolean = false,
    /** Code pays ISO (« FR ») ; matché sur le suffixe de location_name (« Nom, CC »). */
    val countryCode: String? = null,
    val countryNegated: Boolean = false,
    val persons: List<PersonCriterion> = emptyList()
) {
    data class PersonCriterion(val personId: Long, val negated: Boolean)
}

/**
 * Construit la requête SQL (texte + arguments positionnels) des filtres durs.
 * Fonction pure : testable sans Room ni Android.
 */
internal fun buildPhotoSearchQuery(criteria: PhotoSearchCriteria): Pair<String, List<Any>> {
    val sql = StringBuilder(
        "SELECT id, file_path, file_name, date_added, date_taken, is_favorite " +
                "FROM photos WHERE is_hidden = 0"
    )
    val args = mutableListOf<Any>()

    if (criteria.startMs != null && criteria.endMs != null) {
        sql.append(
            if (criteria.dateNegated) " AND date_taken NOT BETWEEN ? AND ?"
            else " AND date_taken BETWEEN ? AND ?"
        )
        args += criteria.startMs
        args += criteria.endMs
    }

    if (criteria.mediaType != null) {
        sql.append(if (criteria.typeNegated) " AND media_type != ?" else " AND media_type = ?")
        args += criteria.mediaType
    }

    if (criteria.minLat != null && criteria.maxLat != null &&
        criteria.minLon != null && criteria.maxLon != null
    ) {
        val inBox = "(latitude IS NOT NULL AND longitude IS NOT NULL " +
                "AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?)"
        // Nié : une photo sans GPS n'est pas « à Lille » → elle est incluse.
        sql.append(if (criteria.geoNegated) " AND NOT $inBox" else " AND $inBox")
        args += criteria.minLat
        args += criteria.maxLat
        args += criteria.minLon
        args += criteria.maxLon
    }

    if (criteria.countryCode != null) {
        // location_name = « Nom, CC » (format contrôlé au géocodage) → suffixe fiable.
        // Nié : une photo sans lieu connu est incluse dans « pas en France ».
        val inCountry = "(location_name IS NOT NULL AND location_name LIKE ?)"
        sql.append(if (criteria.countryNegated) " AND NOT $inCountry" else " AND $inCountry")
        args += "%, ${criteria.countryCode}"
    }

    for (person in criteria.persons) {
        val exists =
            "EXISTS (SELECT 1 FROM faces WHERE faces.photo_id = photos.id AND faces.person_id = ?)"
        sql.append(if (person.negated) " AND NOT $exists" else " AND $exists")
        args += person.personId
    }

    if (!criteria.text.isNullOrBlank()) {
        sql.append(" AND (file_name LIKE ? ESCAPE '\\' OR relative_path LIKE ? ESCAPE '\\')")
        val pattern = "%${criteria.text.escapeLikePattern()}%"
        args += pattern
        args += pattern
    }

    sql.append(" ORDER BY date_taken DESC")
    return sql.toString() to args
}
