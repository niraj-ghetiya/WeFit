package com.wefit.app.core.util

import com.wefit.app.domain.model.UnitSystem
import kotlin.math.roundToInt

object UnitConverter {
    const val KG_PER_LB = 0.45359237
    const val KM_PER_MILE = 1.609344
    const val CM_PER_INCH = 2.54

    fun kgToLb(kg: Double): Double = kg / KG_PER_LB
    fun lbToKg(lb: Double): Double = lb * KG_PER_LB
    fun kmToMiles(km: Double): Double = km / KM_PER_MILE
    fun milesToKm(mi: Double): Double = mi * KM_PER_MILE

    /** Returns feet and remaining whole inches. */
    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun feetInchesToCm(feet: Int, inches: Int): Double = (feet * 12 + inches) * CM_PER_INCH

    fun weightToDisplay(kg: Double, units: UnitSystem): Double =
        if (units == UnitSystem.IMPERIAL) kgToLb(kg) else kg

    fun weightFromDisplay(value: Double, units: UnitSystem): Double =
        if (units == UnitSystem.IMPERIAL) lbToKg(value) else value

    fun distanceToDisplay(km: Double, units: UnitSystem): Double =
        if (units == UnitSystem.IMPERIAL) kmToMiles(km) else km

    fun distanceFromDisplay(value: Double, units: UnitSystem): Double =
        if (units == UnitSystem.IMPERIAL) milesToKm(value) else value

    fun weightUnit(units: UnitSystem) = if (units == UnitSystem.IMPERIAL) "lb" else "kg"
    fun distanceUnit(units: UnitSystem) = if (units == UnitSystem.IMPERIAL) "mi" else "km"
}
