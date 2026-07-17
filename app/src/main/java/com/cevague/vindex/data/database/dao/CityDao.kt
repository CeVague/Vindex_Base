package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.cevague.vindex.data.database.entity.City
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Au-delà, une « ville la plus proche » ne décrit plus le lieu de la photo :
 * on préfère laisser `location_name` vide.
 */
private const val MAX_GEOCODE_KM = 100.0

private const val KM_PER_DEGREE_LAT = 111.0

/** Distance approchée en km (équirectangulaire) : largement assez juste pour un plafond. */
private fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1) * KM_PER_DEGREE_LAT
    val dLon = (lon2 - lon1) * KM_PER_DEGREE_LAT * cos(Math.toRadians((lat1 + lat2) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

@Dao
interface CityDao {

    @Query("SELECT * FROM cities WHERE id = :id")
    suspend fun getCityById(id: Long): City?

    @Query("SELECT * FROM cities WHERE name = :name AND country_code = :countryCode LIMIT 1")
    suspend fun getCityByNameAndCountry(name: String, countryCode: String): City?

    /** Exonymes d'une ville (« Munich » pour München), pour le matching des requêtes. */
    @Query("SELECT alias FROM city_aliases WHERE city_id = :cityId")
    suspend fun getAliasesForCity(cityId: Long): List<String>


    suspend fun findNearestCity(lat: Double, lon: Double): City? {
        // Deltas progressifs : ~1 km, ~10 km, ~100 km. Pas de repli illimité :
        // une photo en pleine mer ou en zone isolée recevait l'étiquette de la
        // ville la plus proche, fût-elle à des centaines de kilomètres — mieux
        // vaut aucune étiquette qu'une étiquette absurde.
        val deltas = listOf(0.01, 0.1, 1.0)

        for (delta in deltas) {
            val city = findNearestCityWithDelta(lat, lon, delta)
            if (city != null) {
                // Un delta plus large ne trouverait que plus loin : si la plus
                // proche dépasse déjà le plafond, la réponse est « aucune ».
                return city.takeIf {
                    approxDistanceKm(lat, lon, it.latitude, it.longitude) <= MAX_GEOCODE_KM
                }
            }
        }
        return null
    }

    @Query(
        """
        SELECT *, 
        ((:lat - latitude) * (:lat - latitude) + (:lon - longitude) * (:lon - longitude)) AS distance
        FROM cities 
        WHERE latitude BETWEEN :lat - :delta AND :lat + :delta
          AND longitude BETWEEN :lon - :delta AND :lon + :delta
        ORDER BY distance ASC 
        LIMIT 1
    """
    )
    suspend fun findNearestCityWithDelta(lat: Double, lon: Double, delta: Double): City?

    @Query("SELECT COUNT(*) FROM cities")
    suspend fun getCount(): Int

    // Pas de @Insert ici : l'import passe par l'ATTACH SQL du CityImportWorker.
    // Les anciens insert en OnConflictStrategy.REPLACE étaient une mine — REPLACE
    // sur `cities` (référencée par `city_aliases` en CASCADE) aurait effacé les
    // alias de chaque ville ré-insérée.

    @Query("DELETE FROM cities")
    suspend fun deleteAll()
}
