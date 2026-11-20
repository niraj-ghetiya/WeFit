package com.wefit.app.core.util

import com.wefit.app.domain.model.UnitSystem
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val fullDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
    private val mediumDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)
    fun number(value: Long): String = NumberFormat.getIntegerInstance().format(value)

    fun decimal(value: Double, digits: Int = 1): String = String.format(Locale.getDefault(), "%.${digits}f", value)

    fun weight(kg: Double, units: UnitSystem): String =
        "${decimal(UnitConverter.weightToDisplay(kg, units))} ${UnitConverter.weightUnit(units)}"

    fun weightValue(kg: Double, units: UnitSystem): String = decimal(UnitConverter.weightToDisplay(kg, units))

    /** Signed weight change such as "-1.5 kg" or "+0.4 lb". */
    fun weightChange(deltaKg: Double, units: UnitSystem): String {
        val v = UnitConverter.weightToDisplay(deltaKg, units)
        val sign = when {
            v > 0.049 -> "+"
            v < -0.049 -> "-"
            else -> ""
        }
        return "$sign${decimal(abs(v))} ${UnitConverter.weightUnit(units)}"
    }

    fun distance(km: Double, units: UnitSystem): String =
        "${decimal(UnitConverter.distanceToDisplay(km, units), if (km < 10) 2 else 1)} ${UnitConverter.distanceUnit(units)}"

    fun height(cm: Double, units: UnitSystem): String = if (units == UnitSystem.IMPERIAL) {
        val (ft, inch) = UnitConverter.cmToFeetInches(cm)
        "$ft ft $inch in"
    } else "${cm.toInt()} cm"

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    fun fullDate(date: LocalDate): String = date.format(fullDate)
    fun mediumDate(date: LocalDate): String = date.format(mediumDate)
    fun shortDate(date: LocalDate): String = date.format(shortDate)

    fun time(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return String.format(Locale.getDefault(), "%d:%02d %s", h12, minute, if (hour < 12) "AM" else "PM")
    }

    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12A"
        hour < 12 -> "${hour}A"
        hour == 12 -> "12P"
        else -> "${hour - 12}P"
    }

    fun percent(value: Double): String = "${(value * 100).toInt()}%"
}
