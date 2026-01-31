package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.entity.City
import com.cevague.vindex.data.local.SettingsCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityRepository @Inject constructor(
    private val cityDao: CityDao,
    private val settingsCache: SettingsCache
) {

    suspend fun isDatabasePopulated(): Boolean {
        if (settingsCache.isCitiesLoaded) return true

        val count = cityDao.getCount()
        val isPopulated = count > 0

        if (isPopulated) {
            settingsCache.isCitiesLoaded = true
        }

        return isPopulated
    }

    suspend fun getCityById(id: Long): City? = cityDao.getCityById(id)

    suspend fun getCityByNameAndCountry(name: String, countryCode: String): City? =
        cityDao.getCityByNameAndCountry(name, countryCode)

    suspend fun findNearestCity(lat: Double, lon: Double): City? =
        cityDao.findNearestCity(lat, lon)

    suspend fun getCount(): Int = cityDao.getCount()

    suspend fun insert(city: City) {
        cityDao.insert(city)
        settingsCache.isCitiesLoaded = true
    }

    suspend fun insertAll(cities: List<City>) {
        cityDao.insertAll(cities)
        settingsCache.isCitiesLoaded = true
    }

    suspend fun deleteAll() {
        cityDao.deleteAll()
        settingsCache.isCitiesLoaded = false
    }

}