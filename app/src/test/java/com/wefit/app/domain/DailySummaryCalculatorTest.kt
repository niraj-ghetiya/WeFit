package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.domain.calc.DailySummaryCalculator
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

class DailySummaryCalculatorTest {
    private val day = LocalDate.of(2026, 11, 20)
    private fun activity(type: ActivityType, minutes: Int, km: Double? = null, kcal: Int? = null) =
        ActivityEntry(type = type, date = day, durationMinutes = minutes, distanceKm = km, caloriesKcal = kcal)

    @Test fun stepsOnly() {
        val s = DailySummaryCalculator.summarize(10_000, emptyList(), 180.0, 70.0)
        assertThat(s.distanceKm).isWithin(1e-6).of(7.452)
        assertThat(s.activeMinutes).isEqualTo(100)
        assertThat(s.caloriesKcal).isEqualTo(FitnessCalculator.stepCalories(10_000, 180.0, 70.0).roundToInt())
        assertThat(s.overlapResolved).isFalse()
    }

    @Test fun walkingEntryDoesNotDoubleCountSensorSteps() {
        // 6,000 steps ≈ 4.47 km, 60 min. A manual 3 km / 30 min walk is already inside those steps.
        val stepsOnly = DailySummaryCalculator.summarize(6_000, emptyList(), 180.0, 70.0)
        val withWalk = DailySummaryCalculator.summarize(6_000, listOf(activity(ActivityType.WALKING, 30, 3.0, 120)), 180.0, 70.0)
        assertThat(withWalk.distanceKm).isEqualTo(stepsOnly.distanceKm)
        assertThat(withWalk.activeMinutes).isEqualTo(stepsOnly.activeMinutes)
        assertThat(withWalk.caloriesKcal).isEqualTo(stepsOnly.caloriesKcal)
        assertThat(withWalk.overlapResolved).isTrue()
    }

    @Test fun manualRunLargerThanSensorWins() {
        // Phone left at home: few sensor steps, but a 10 km run was logged.
        val s = DailySummaryCalculator.summarize(500, listOf(activity(ActivityType.RUNNING, 55, 10.0, 650)), 180.0, 70.0)
        assertThat(s.distanceKm).isEqualTo(10.0)
        assertThat(s.activeMinutes).isEqualTo(55)
        assertThat(s.caloriesKcal).isEqualTo(650)
    }

    @Test fun cyclingIsAddedOnTopOfSteps() {
        val stepsOnly = DailySummaryCalculator.summarize(5_000, emptyList(), 180.0, 70.0)
        val s = DailySummaryCalculator.summarize(5_000, listOf(activity(ActivityType.CYCLING, 40, 15.0, 300)), 180.0, 70.0)
        assertThat(s.caloriesKcal).isEqualTo(stepsOnly.caloriesKcal + 300)
        assertThat(s.activeMinutes).isEqualTo(stepsOnly.activeMinutes + 40)
        // Distance is "walked" distance; cycling is not added.
        assertThat(s.distanceKm).isEqualTo(stepsOnly.distanceKm)
    }

    @Test fun missingCaloriesAreEstimatedWithMet() {
        val s = DailySummaryCalculator.summarize(0, listOf(activity(ActivityType.OTHER, 60)), null, 80.0)
        assertThat(s.caloriesKcal).isEqualTo((4.0 * 80.0).roundToInt())
    }
}
