package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.entity.City
import com.cevague.vindex.data.local.FastSettings

class CityRepository(private val cityDao: CityDao) {

    suspend fun isDatabasePopulated(): Boolean {
        if (FastSettings.isCitiesLoaded) return true

        val count = cityDao.getCount()
        val isPopulated = count > 0

        if (isPopulated) {
            FastSettings.isCitiesLoaded = true
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
        FastSettings.isCitiesLoaded = true
    }

    suspend fun insertAll(cities: List<City>) {
        cityDao.insertAll(cities)
        FastSettings.isCitiesLoaded = true
    }

    suspend fun deleteAll() {
        cityDao.deleteAll()
        FastSettings.isCitiesLoaded = false
    }

}