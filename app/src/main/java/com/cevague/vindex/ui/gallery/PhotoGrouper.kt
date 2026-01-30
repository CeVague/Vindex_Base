package com.cevague.vindex.ui.gallery

import android.content.Context
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.PhotoSummary
import java.text.SimpleDateFormat
import java.util.*

class PhotoGrouper(private val context: Context) {

    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())

    fun groupByDate(photos: List<PhotoSummary>): List<GalleryItem> {
        if (photos.isEmpty()) return emptyList()

        val result = mutableListOf<GalleryItem>()

        var currentCategory: String? = null

        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)
        val weekStart = getStartOfWeek(now)
        val monthStart = getStartOfMonth(now)
        val yearStart = getStartOfYear(now)


        // Parcourir toutes les photos
        for (photo in photos) {
            // Déterminer dans quelle catégorie tombe cette photo
            val category = getCategoryForDate(
                timestamp = photo.dateTaken?:0L,
                todayStart = todayStart,
                weekStart = weekStart,
                monthStart = monthStart,
                yearStart = yearStart
            )

            // Si on change de catégorie, ajouter un nouveau header
            if (category != currentCategory) {
                currentCategory = category
                result.add(GalleryItem.Header(
                    title = category,
                    // ID unique basé sur le titre (pour DiffUtil)
                    id = "header_${category.lowercase().replace(" ", "_")}"
                ))
            }

            // Ajouter la photo
            result.add(GalleryItem.PhotoItem(photo))
        }

        return result
    }

    private fun getCategoryForDate(
        timestamp: Long,
        todayStart: Long,
        weekStart: Long,
        monthStart: Long,
        yearStart: Long
    ): String {
        return when {
            // Photo prise aujourd'hui
            timestamp >= todayStart -> {
                context.getString(R.string.date_today)
            }

            // Photo prise cette semaine (mais pas aujourd'hui)
            timestamp >= weekStart -> {
                context.getString(R.string.date_this_week)
            }

            // Photo prise ce mois-ci (mais pas cette semaine)
            timestamp >= monthStart -> {
                context.getString(R.string.date_this_month)
            }

            timestamp >= yearStart -> {
                context.getString(
                    R.string.date_custom,
                    monthFormatter.format(Date(timestamp)).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                        else it.toString()
                    }
                )
            }

            else -> {
                context.getString(
                    R.string.date_custom,
                    monthYearFormatter.format(Date(timestamp)).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                        else it.toString()
                    }
                )
            }
        }
    }


    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfWeek(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // firstDayOfWeek est automatiquement correct selon la locale
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        return calendar.timeInMillis
    }

    private fun getStartOfMonth(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfYear(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}