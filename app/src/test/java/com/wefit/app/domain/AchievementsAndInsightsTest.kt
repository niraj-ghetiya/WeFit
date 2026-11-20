package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.domain.calc.AchievementCatalog
import com.wefit.app.domain.calc.AchievementEvaluator
import com.wefit.app.domain.calc.AchievementStats
import com.wefit.app.domain.calc.InsightsCalculator
import com.wefit.app.domain.calc.InsightsRange
import com.wefit.app.domain.calc.ReminderTimeCalculator
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.model.WeightEntry
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class AchievementsAndInsightsTest {
    private val today = LocalDate.of(2026, 11, 20) // Friday

    private fun steps(daysAgo: Long, total: Int, goal: Int = 10_000) =
        DailySteps(today.minusDays(daysAgo), sensorSteps = total, goal = goal)

    @Test fun earnedAchievementsFromStats() {
        val earned = AchievementEvaluator.earned(AchievementStats(maxDailySteps = 6_000, weightEntryCount = 1))
        assertThat(earned).containsExactly("steps_1k", "steps_5k", "first_weight")
    }

    @Test fun noDataEarnsNothing() {
        assertThat(AchievementEvaluator.earned(AchievementStats())).isEmpty()
    }

    @Test fun progressIsFractionOfTarget() {
        val def = AchievementCatalog.byId("steps_10k")!!
        assertThat(AchievementEvaluator.progress(def, AchievementStats(maxDailySteps = 2_500))).isEqualTo(0.25f)
    }

    @Test fun buildStatsComputesStreaksWeightChangeAndWeeklyGoal() {
        val stepDays = (0L..6L).map { steps(it, 10_500) }
        val weights = listOf(
            WeightEntry(1, 80.0, today.minusDays(20), createdAt = 1),
            WeightEntry(2, 78.8, today, createdAt = 2),
        )
        val stats = AchievementEvaluator.buildStats(stepDays, weights, emptyList(), FitnessGoals(weeklyActiveMinutesGoal = 150), 175.0)
        assertThat(stats.longestActivityStreak).isEqualTo(7)
        assertThat(stats.maxWeightChangeKg).isWithin(1e-9).of(1.2)
        assertThat(stats.weeklyGoalCompleted).isTrue()
        assertThat(stats.lifetimeSteps).isEqualTo(73_500)
        assertThat(AchievementEvaluator.earned(stats)).containsAtLeast("streak_7", "weight_1kg", "weekly_goal", "steps_10k")
    }

    @Test fun activityDaysCountTowardActivityStreak() {
        val acts = (0L..6L).map { ActivityEntry(type = ActivityType.CYCLING, date = today.minusDays(it), durationMinutes = 20) }
        val stats = AchievementEvaluator.buildStats(emptyList(), emptyList(), acts, FitnessGoals(), null)
        assertThat(stats.longestActivityStreak).isEqualTo(7)
        assertThat(stats.activityCount).isEqualTo(7)
    }

    @Test fun weeklyInsightsAveragesOnlyTrackedDays() {
        // Tracking started Wednesday; week is Mon 16 – Sun 22; today is Friday.
        val data = listOf(steps(2, 6_000), steps(1, 9_000), steps(0, 12_000))
        val insights = InsightsCalculator.compute(InsightsRange.WEEK, today, today, data, emptyList(), emptyList(), 175.0)
        assertThat(insights.start).isEqualTo(LocalDate.of(2026, 11, 16))
        assertThat(insights.end).isEqualTo(LocalDate.of(2026, 11, 22))
        assertThat(insights.totalSteps).isEqualTo(27_000)
        assertThat(insights.avgDailySteps).isEqualTo(9_000)
        assertThat(insights.goalDaysMet).isEqualTo(1)
        assertThat(insights.goalDaysCounted).isEqualTo(3)
        assertThat(insights.bars).hasSize(7)
        assertThat(insights.bars[4].highlighted).isTrue() // Friday
        assertThat(insights.previousAvgDailySteps).isNull()
    }

    @Test fun insightsCompareWithPreviousPeriod() {
        val data = (0L..13L).map { steps(it, if (it <= 4) 11_000 else 10_000) }
        val insights = InsightsCalculator.compute(InsightsRange.WEEK, today, today, data, emptyList(), emptyList(), null)
        assertThat(insights.avgDailySteps).isEqualTo(11_000)
        assertThat(insights.previousAvgDailySteps).isEqualTo(10_000)
        assertThat(insights.stepChangeFraction).isWithin(1e-9).of(0.1)
    }

    @Test fun weightChangeWithinPeriod() {
        val weights = listOf(
            WeightEntry(1, 74.0, LocalDate.of(2026, 11, 2)),
            WeightEntry(2, 72.5, LocalDate.of(2026, 11, 18)),
            WeightEntry(3, 70.0, LocalDate.of(2026, 10, 1)),
        )
        val insights = InsightsCalculator.compute(InsightsRange.MONTH, today, today, emptyList(), weights, emptyList(), null)
        assertThat(insights.weightPoints.map { it.second }).containsExactly(74.0, 72.5).inOrder()
        assertThat(insights.weightChangeKg).isWithin(1e-9).of(-1.5)
        assertThat(insights.bars).hasSize(30)
    }

    @Test fun yearInsightsHaveTwelveMonthlyBars() {
        val insights = InsightsCalculator.compute(InsightsRange.YEAR, today, today, listOf(steps(0, 8_000)), emptyList(), emptyList(), null)
        assertThat(insights.bars).hasSize(12)
        assertThat(insights.bars[10].value).isEqualTo(8_000f)
    }

    @Test fun dailyReminderTriggersTodayOrTomorrow() {
        val setting = ReminderSetting(ReminderType.WEIGHT, enabled = true, hour = 8, minute = 0)
        val morning = LocalDateTime.of(2026, 11, 20, 7, 0).atZone(ZoneOffset.UTC)
        assertThat(ReminderTimeCalculator.nextTrigger(setting, morning).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 20, 8, 0))
        val evening = LocalDateTime.of(2026, 11, 20, 8, 0).atZone(ZoneOffset.UTC)
        assertThat(ReminderTimeCalculator.nextTrigger(setting, evening).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 21, 8, 0))
    }

    @Test fun weeklyReminderTriggersOnChosenDay() {
        val setting = ReminderSetting(ReminderType.WEEKLY_SUMMARY, enabled = true, hour = 19, minute = 0, dayOfWeek = DayOfWeek.SUNDAY)
        val friday = LocalDateTime.of(2026, 11, 20, 12, 0).atZone(ZoneOffset.UTC)
        assertThat(ReminderTimeCalculator.nextTrigger(setting, friday).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 22, 19, 0))
        val sundayLate = LocalDateTime.of(2026, 11, 22, 20, 0).atZone(ZoneOffset.UTC)
        assertThat(ReminderTimeCalculator.nextTrigger(setting, sundayLate).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 29, 19, 0))
    }
}
