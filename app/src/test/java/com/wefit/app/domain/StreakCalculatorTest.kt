package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.domain.calc.StreakCalculator
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test fun currentStreakIncludingToday() {
        assertThat(StreakCalculator.currentStreak(days(0, 1, 2, 4), today)).isEqualTo(3)
    }

    @Test fun currentStreakStillAliveIfOnlyYesterdayQualified() {
        assertThat(StreakCalculator.currentStreak(days(1, 2, 3), today)).isEqualTo(3)
    }

    @Test fun brokenStreakIsZero() {
        assertThat(StreakCalculator.currentStreak(days(2, 3), today)).isEqualTo(0)
        assertThat(StreakCalculator.currentStreak(emptySet(), today)).isEqualTo(0)
    }

    @Test fun longestStreak() {
        assertThat(StreakCalculator.longestStreak(days(0, 1, 5, 6, 7, 8, 20))).isEqualTo(4)
        assertThat(StreakCalculator.longestStreak(emptySet())).isEqualTo(0)
    }

    @Test fun streakSpansMonthBoundary() {
        val set = (0L..9L).map { LocalDate.of(2026, 3, 5).minusDays(it) }.toSet()
        assertThat(StreakCalculator.longestStreak(set)).isEqualTo(10)
    }
}
