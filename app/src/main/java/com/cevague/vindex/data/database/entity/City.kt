package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pre-populated table for reverse geocoding (GPS to city name).
 * Data source: GeoNames cities15000.txt
 */
@Entity(
    tableName = "cities",
    indices = [
        Index(value = ["latitude", "longitude"])
    ]
)
data class City(
    @PrimaryKey
    val id: Long,

    val name: String,

    @ColumnInfo(name = "country_code")
    val countryCode: String,

    val latitude: Double,

    val longitude: Double,

    val population: Int? = null
)
