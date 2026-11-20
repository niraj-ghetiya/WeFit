package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.model.ActivityType
import org.junit.Test

class FitnessCalculatorTest {

    @Test fun strideUsesHeightWhenKnown() {
        assertThat(FitnessCalculator.strideLengthMeters(180.0)).isWithin(1e-6).of(0.7452)
    }

    @Test fun strideFallsBackToDefaultForMissingOrInvalidHeight() {
        assertThat(FitnessCalculator.strideLengthMeters(null)).isEqualTo(FitnessCalculator.DEFAULT_STRIDE_M)
        assertThat(FitnessCalculator.strideLengthMeters(10.0)).isEqualTo(FitnessCalculator.DEFAULT_STRIDE_M)
    }

    @Test fun distanceFromSteps() {
        // 10,000 steps * 0.7452 m = 7.452 km
        assertThat(FitnessCalculator.distanceKm(10_000, 180.0)).isWithin(1e-6).of(7.452)
        assertThat(FitnessCalculator.distanceKm(-5, 180.0)).isEqualTo(0.0)
    }

    @Test fun stepCaloriesScaleWithWeightAndDistance() {
        val light = FitnessCalculator.stepCalories(8_000, 170.0, 60.0)
        val heavy = FitnessCalculator.stepCalories(8_000, 170.0, 90.0)
        assertThat(heavy).isWithin(1e-6).of(light * 1.5)
        // ~8,420 steps at 72.5 kg / 175 cm is roughly 300-400 kcal
        assertThat(FitnessCalculator.stepCalories(8_420, 175.0, 72.5)).isIn(com.google.common.collect.Range.closed(300.0, 400.0))
    }

    @Test fun metCalories() {
        // Running MET 9.8 * 70 kg * 0.5 h = 343
        assertThat(FitnessCalculator.activityCalories(ActivityType.RUNNING, 30, 70.0)).isWithin(1e-6).of(343.0)
    }

    @Test fun walkingMinutesFromCadence() {
        assertThat(FitnessCalculator.walkingMinutes(5_000)).isEqualTo(50)
    }

    @Test fun goalProgressIsClamped() {
        assertThat(FitnessCalculator.goalProgress(15_000, 10_000)).isEqualTo(1f)
        assertThat(FitnessCalculator.goalProgress(2_500, 10_000)).isEqualTo(0.25f)
        assertThat(FitnessCalculator.goalProgress(100, 0)).isEqualTo(0f)
    }

    @Test fun weightGoalProgressHandlesLossAndGain() {
        assertThat(FitnessCalculator.weightGoalProgress(80.0, 75.0, 70.0)).isWithin(1e-6f).of(0.5f)
        assertThat(FitnessCalculator.weightGoalProgress(60.0, 63.0, 66.0)).isWithin(1e-6f).of(0.5f)
        assertThat(FitnessCalculator.weightGoalProgress(80.0, 82.0, 70.0)).isEqualTo(0f)
        assertThat(FitnessCalculator.weightGoalProgress(80.0, null, 70.0)).isEqualTo(0f)
    }

    @Test fun unitConversionsRoundTrip() {
        assertThat(UnitConverter.lbToKg(UnitConverter.kgToLb(72.5))).isWithin(1e-9).of(72.5)
        assertThat(UnitConverter.kgToLb(1.0)).isWithin(1e-4).of(2.2046)
        assertThat(UnitConverter.cmToFeetInches(180.0)).isEqualTo(5 to 11)
        assertThat(UnitConverter.feetInchesToCm(6, 0)).isWithin(1e-9).of(182.88)
        assertThat(UnitConverter.kmToMiles(1.609344)).isWithin(1e-9).of(1.0)
    }
}
