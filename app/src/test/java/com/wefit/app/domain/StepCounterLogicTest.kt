package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.domain.calc.SensorSnapshot
import com.wefit.app.domain.calc.StepCounterLogic
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StepCounterLogicTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0, day: Int = 20) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val startOfDay = at(0)

    @Test fun firstReadingAfterBootTodayCreditsStepsSinceBoot() {
        val d = StepCounterLogic.computeDelta(null, 1_200, 7, now = at(10), bootTime = at(8), startOfToday = startOfDay)
        assertThat(d.steps).isEqualTo(1_200)
        assertThat(d.intervalStart).isEqualTo(at(8))
        assertThat(d.snapshot).isEqualTo(SensorSnapshot(1_200, 7, at(10)))
    }

    @Test fun firstReadingWithBootBeforeTodayOnlySetsBaseline() {
        val d = StepCounterLogic.computeDelta(null, 50_000, 7, now = at(10), bootTime = at(8, day = 18), startOfToday = startOfDay)
        assertThat(d.steps).isEqualTo(0)
        assertThat(d.snapshot.counterValue).isEqualTo(50_000)
    }

    @Test fun normalReadingCreditsDifferenceOnly() {
        val prev = SensorSnapshot(10_000, 7, at(9))
        val d = StepCounterLogic.computeDelta(prev, 10_850, 7, now = at(9, 15), bootTime = at(0, day = 18), startOfToday = startOfDay)
        assertThat(d.steps).isEqualTo(850)
        assertThat(d.rebootDetected).isFalse()
    }

    @Test fun repeatedReadingIsNotDoubleCounted() {
        val prev = SensorSnapshot(10_000, 7, at(9))
        val d = StepCounterLogic.computeDelta(prev, 10_000, 7, now = at(9, 5), bootTime = 0, startOfToday = startOfDay)
        assertThat(d.steps).isEqualTo(0)
    }

    @Test fun rebootDetectedByCounterGoingBackwards() {
        val prev = SensorSnapshot(25_000, StepCounterLogic.UNKNOWN_BOOT_COUNT, at(9))
        val d = StepCounterLogic.computeDelta(prev, 300, StepCounterLogic.UNKNOWN_BOOT_COUNT, now = at(11), bootTime = at(10), startOfToday = startOfDay)
        assertThat(d.rebootDetected).isTrue()
        assertThat(d.steps).isEqualTo(300)
        assertThat(d.intervalStart).isEqualTo(at(10))
    }

    @Test fun rebootDetectedByBootCountEvenIfCounterIsHigher() {
        // Previous boot: counter 1,000. New boot: user already walked 4,000 steps.
        val prev = SensorSnapshot(1_000, 7, at(8))
        val d = StepCounterLogic.computeDelta(prev, 4_000, 8, now = at(12), bootTime = at(9), startOfToday = startOfDay)
        assertThat(d.rebootDetected).isTrue()
        assertThat(d.steps).isEqualTo(4_000)
    }

    @Test fun implausibleJumpIsCapped() {
        val prev = SensorSnapshot(0, 7, at(9))
        val d = StepCounterLogic.computeDelta(prev, 1_000_000, 7, now = at(9, 10), bootTime = 0, startOfToday = startOfDay)
        assertThat(d.steps).isEqualTo(10 * StepCounterLogic.MAX_STEPS_PER_MINUTE)
    }

    @Test fun allocationSpreadsAcrossHoursAndSumsExactly() {
        val result = StepCounterLogic.allocate(1_001, at(9, 30), at(11, 0), zone)
        assertThat(result.values.sum()).isEqualTo(1_001)
        val d = LocalDate.of(2026, 11, 20)
        // 30 min in hour 9, 60 min in hour 10 -> 1/3 and 2/3
        assertThat(result[d to 9]).isEqualTo(333)
        assertThat(result[d to 10]!! + (result[d to 11] ?: 0)).isEqualTo(668)
    }

    @Test fun allocationAcrossMidnightSplitsDays() {
        val result = StepCounterLogic.allocate(600, at(23, 30, day = 19), at(0, 30, day = 20), zone)
        assertThat(result[LocalDate.of(2026, 11, 19) to 23]).isEqualTo(300)
        assertThat(result[LocalDate.of(2026, 11, 20) to 0]).isEqualTo(300)
    }

    @Test fun longGapCreditsCurrentHour() {
        val result = StepCounterLogic.allocate(5_000, at(6, day = 19), at(14), zone)
        assertThat(result).containsExactly(LocalDate.of(2026, 11, 20) to 14, 5_000L)
    }

    @Test fun zeroStepsAllocateNothing() {
        assertThat(StepCounterLogic.allocate(0, at(9), at(10), zone)).isEmpty()
    }
}
