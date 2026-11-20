package com.wefit.app.domain.calc

import java.time.LocalDate

object StreakCalculator {
    /**
     * Consecutive qualifying days ending today. If today does not qualify yet, the streak ending
     * yesterday is still "alive" and is returned.
     */
    fun currentStreak(qualifyingDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in qualifyingDays) today else today.minusDays(1)
        var count = 0
        while (day in qualifyingDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(qualifyingDays: Set<LocalDate>): Int {
        if (qualifyingDays.isEmpty()) return 0
        val sorted = qualifyingDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
