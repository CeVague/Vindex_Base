package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cevague.vindex.data.database.entity.City

@Dao
interface CityDao {

    @Query("SELECT * FROM cities WHERE id = :id")
    suspend fun getCityById(id: Long): City?

    @Query("SELECT * FROM cities WHERE name = :name AND country_code = :countryCode LIMIT 1")
    suspend fun getCityByNameAndCountry(name: String, countryCode: String): City?


    suspend fun findNearestCity(lat: Double, lon: Double): City? {
        // Deltas progressifs : ~1km, ~10km, ~100km, ~1000km
        val deltas = listOf(0.01, 0.1, 1.0, 10.0)

        for (delta in deltas) {
            val city = findNearestCityWithDelta(lat, lon, delta)
            if (city != null) return city
        }

        return findNearestCitySlow(lat, lon)
    }

    @Query("""
        SELECT *, 
        ((:lat - latitude) * (:lat - latitude) + (:lon - longitude) * (:lon - longitude)) AS distance
        FROM cities 
        WHERE latitude BETWEEN :lat - :delta AND :lat + :delta
          AND longitude BETWEEN :lon - :delta AND :lon + :delta
        ORDER BY distance ASC 
        LIMIT 1
    """)
    suspend fun findNearestCityWithDelta(lat: Double, lon: Double, delta: Double): City?

    @Query("""
        SELECT *, 
        ((:lat - latitude) * (:lat - latitude) + (:lon - longitude) * (:lon - longitude)) AS distance
        FROM cities 
        ORDER BY distance ASC 
        LIMIT 1
    """)
    suspend fun findNearestCitySlow(lat: Double, lon: Double): City?

    @Query("SELECT COUNT(*) FROM cities")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: City)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cities: List<City>)

    @Query("DELETE FROM cities")
    suspend fun deleteAll()
}
