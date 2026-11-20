package com.wefit.app.domain.calc

import com.wefit.app.domain.model.ActivityEntry
import kotlin.math.max
import kotlin.math.roundToInt

data class DailySummary(
    val steps: Int,
    val distanceKm: Double,
    val caloriesKcal: Int,
    val activeMinutes: Int,
    /** True when manual walking/running entries overlapped with sensor steps and the higher value was used. */
    val overlapResolved: Boolean,
)

/**
 * Combines sensor steps with manually logged activities without double counting.
 *
 * Walking and running are already captured by the step counter, so for those types we take the
 * HIGHER of the step-derived estimate and the manual entries instead of adding them together.
 * Cycling and other activities are not step-based and are always added on top.
 */
object DailySummaryCalculator {
    fun summarize(
        steps: Int,
        activities: List<ActivityEntry>,
        heightCm: Double?,
        weightKg: Double?,
    ): DailySummary {
        val stepDistance = FitnessCalculator.distanceKm(steps, heightCm)
        val stepCalories = FitnessCalculator.stepCalories(steps, heightCm, weightKg)
        val stepMinutes = FitnessCalculator.walkingMinutes(steps)

        val (stepBased, other) = activities.partition { it.type.isStepBased }
        fun calories(a: ActivityEntry): Double =
            a.caloriesKcal?.toDouble() ?: FitnessCalculator.activityCalories(a.type, a.durationMinutes, weightKg)

        val manualDistance = stepBased.sumOf { it.distanceKm ?: 0.0 }
        val manualCalories = stepBased.sumOf(::calories)
        val manualMinutes = stepBased.sumOf { it.durationMinutes }

        return DailySummary(
            steps = steps,
            distanceKm = max(stepDistance, manualDistance),
            caloriesKcal = (max(stepCalories, manualCalories) + other.sumOf(::calories)).roundToInt(),
            activeMinutes = max(stepMinutes, manualMinutes) + other.sumOf { it.durationMinutes },
            overlapResolved = stepBased.isNotEmpty() && steps > 0,
        )
    }
}
