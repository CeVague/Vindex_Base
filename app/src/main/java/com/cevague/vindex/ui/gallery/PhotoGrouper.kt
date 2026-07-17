package com.cevague.vindex.ui.gallery

import android.content.Context
import com.cevague.vindex.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PhotoGrouper(private val context: Context) {

    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())

    // Cache pour les limites temporelles
    private var lastUpdateDay = -1
    private var todayStart = 0L
    private var weekStart = 0L
    private var monthStart = 0L
    private var yearStart = 0L

    /**
     * Met à jour les bornes temporelles si nécessaire (une seule fois par jour).
     *
     * Chaque borne part d'un calendrier **frais** calé sur aujourd'hui, jamais du
     * calendrier déjà muté par la borne précédente : l'ancien enchaînement faisait
     * dériver le début de semaine dans le mois précédent, puis `DAY_OF_MONTH = 1`
     * donnait le 1er du *mauvais* mois (« Ce mois-ci » couvrait juin début juillet),
     * et `DAY_OF_YEAR = 1` la mauvaise année début janvier.
     */
    private fun updateThresholds() {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_YEAR)

        if (currentDay == lastUpdateDay) return
        lastUpdateDay = currentDay

        todayStart = now.atStartOfDay().timeInMillis
        weekStart = now.atStartOfDay()
            .apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
            .timeInMillis
        monthStart = now.atStartOfDay()
            .apply { set(Calendar.DAY_OF_MONTH, 1) }
            .timeInMillis
        yearStart = now.atStartOfDay()
            .apply { set(Calendar.DAY_OF_YEAR, 1) }
            .timeInMillis
    }

    private fun Calendar.atStartOfDay(): Calendar = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun makeHeader(timestamp: Long): GalleryItem.Header {
        updateThresholds()

        val category = getCategoryForDate(
            timestamp = timestamp,
            todayStart = todayStart,
            weekStart = weekStart,
            monthStart = monthStart,
            yearStart = yearStart
        )

        return GalleryItem.Header(
            title = category,
            // ID unique basé sur le titre (pour DiffUtil)
            id = "header_${category.lowercase().replace(" ", "_")}"
        )
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
}