package com.wefit.app.domain.calc

import com.wefit.app.domain.model.ActivityType
import kotlin.math.roundToInt

/**
 * Estimation formulas. All values are estimates and are labelled as such in the UI.
 */
object FitnessCalculator {
    /** Average adult stride when height is unknown (2.5 ft). */
    const val DEFAULT_STRIDE_M = 0.762
    /** Used for calorie estimates only when the user has not logged a weight. */
    const val DEFAULT_WEIGHT_KG = 70.0
    /** Typical walking cadence used to estimate walking time from steps. */
    const val WALKING_CADENCE_SPM = 100
    /** Walking energy cost: ~0.57 kcal per mile per pound of body weight. */
    private const val KCAL_PER_MILE_PER_LB = 0.57
    private const val STRIDE_HEIGHT_RATIO = 0.414

    fun strideLengthMeters(heightCm: Double?): Double =
        heightCm?.takeIf { it in 50.0..272.0 }?.let { it * STRIDE_HEIGHT_RATIO / 100.0 } ?: DEFAULT_STRIDE_M

    fun distanceKm(steps: Int, heightCm: Double?): Double =
        steps.coerceAtLeast(0) * strideLengthMeters(heightCm) / 1000.0

    fun stepCalories(steps: Int, heightCm: Double?, weightKg: Double?): Double {
        val miles = distanceKm(steps, heightCm) / 1.609344
        val lb = (weightKg ?: DEFAULT_WEIGHT_KG) / 0.45359237
        return miles * lb * KCAL_PER_MILE_PER_LB
    }

    fun walkingMinutes(steps: Int): Int = (steps.coerceAtLeast(0).toDouble() / WALKING_CADENCE_SPM).roundToInt()

    /** MET formula: kcal = MET x kg x hours. */
    fun activityCalories(type: ActivityType, durationMinutes: Int, weightKg: Double?): Double =
        type.met * (weightKg ?: DEFAULT_WEIGHT_KG) * durationMinutes.coerceAtLeast(0) / 60.0

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun goalProgress(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else (value.toFloat() / goal).coerceIn(0f, 1f)

    /**
     * Progress from start weight towards target weight (0..1). Works for both losing and gaining.
     */
    fun weightGoalProgress(startKg: Double?, currentKg: Double?, targetKg: Double?): Float {
        if (startKg == null || currentKg == null || targetKg == null) return 0f
        val total = targetKg - startKg
        if (kotlin.math.abs(total) < 0.05) return if (kotlin.math.abs(currentKg - targetKg) < 0.05) 1f else 0f
        return ((currentKg - startKg) / total).toFloat().coerceIn(0f, 1f)
    }
}
