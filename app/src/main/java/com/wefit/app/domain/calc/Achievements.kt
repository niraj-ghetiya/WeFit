package com.wefit.app.domain.calc

import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.WeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

enum class AchievementIcon { STEPS, STREAK, WEIGHT, CALENDAR, TROPHY, ACTIVITY }

data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val icon: AchievementIcon,
    val target: Double,
    val progressOf: (AchievementStats) -> Double,
)

data class AchievementStats(
    val maxDailySteps: Int = 0,
    val lifetimeSteps: Long = 0,
    val longestActivityStreak: Int = 0,
    val weightEntryCount: Int = 0,
    val maxWeightChangeKg: Double = 0.0,
    val longestTrackingStreak: Int = 0,
    val weeklyGoalCompleted: Boolean = false,
    val activityCount: Int = 0,
)

object AchievementCatalog {
    val all: List<AchievementDefinition> = listOf(
        AchievementDefinition("steps_1k", "First 1,000 Steps", "Walk 1,000 steps in a day", AchievementIcon.STEPS, 1_000.0) { it.maxDailySteps.toDouble() },
        AchievementDefinition("steps_5k", "5K Steps", "Walk 5,000 steps in a day", AchievementIcon.STEPS, 5_000.0) { it.maxDailySteps.toDouble() },
        AchievementDefinition("steps_10k", "10K Steps", "Walk 10,000 steps in a day", AchievementIcon.STEPS, 10_000.0) { it.maxDailySteps.toDouble() },
        AchievementDefinition("first_weight", "First Weigh-In", "Log your first weight entry", AchievementIcon.WEIGHT, 1.0) { it.weightEntryCount.toDouble() },
        AchievementDefinition("first_activity", "First Workout", "Log your first activity", AchievementIcon.ACTIVITY, 1.0) { it.activityCount.toDouble() },
        AchievementDefinition("streak_7", "7-Day Activity Streak", "Hit your step goal or log an activity 7 days in a row", AchievementIcon.STREAK, 7.0) { it.longestActivityStreak.toDouble() },
        AchievementDefinition("weight_1kg", "1 kg Weight Change", "Change your weight by 1 kg from your first entry", AchievementIcon.WEIGHT, 1.0) { it.maxWeightChangeKg },
        AchievementDefinition("weekly_goal", "Weekly Goal Completed", "Reach your weekly active-minutes goal", AchievementIcon.TROPHY, 1.0) { if (it.weeklyGoalCompleted) 1.0 else 0.0 },
        AchievementDefinition("tracking_30", "30-Day Tracking Streak", "Track steps, weight or activity 30 days in a row", AchievementIcon.CALENDAR, 30.0) { it.longestTrackingStreak.toDouble() },
        AchievementDefinition("lifetime_100k", "100K Club", "Walk 100,000 steps in total", AchievementIcon.STEPS, 100_000.0) { it.lifetimeSteps.toDouble() },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

object AchievementEvaluator {
    fun progress(def: AchievementDefinition, stats: AchievementStats): Float =
        (def.progressOf(stats) / def.target).toFloat().coerceIn(0f, 1f)

    fun earned(stats: AchievementStats): Set<String> =
        AchievementCatalog.all.filter { it.progressOf(stats) >= it.target }.map { it.id }.toSet()

    fun buildStats(
        steps: List<DailySteps>,
        weights: List<WeightEntry>,
        activities: List<ActivityEntry>,
        goals: FitnessGoals,
        heightCm: Double?,
    ): AchievementStats {
        val activityDays = activities.map { it.date }.toSet()
        val goalDays = steps.filter { it.goalMet }.map { it.date }.toSet()
        val trackingDays = steps.filter { it.total > 0 }.map { it.date }.toSet() + weights.map { it.date } + activityDays

        val ordered = weights.sortedWith(compareBy({ it.date }, { it.createdAt }))
        val first = ordered.firstOrNull()?.weightKg
        val maxChange = if (first == null) 0.0 else ordered.maxOf { abs(it.weightKg - first) }

        val latestWeight = ordered.lastOrNull()?.weightKg
        val stepsByDate = steps.associateBy { it.date }
        val activitiesByDate = activities.groupBy { it.date }
        val allDates = stepsByDate.keys + activitiesByDate.keys
        val minutesByWeek = allDates.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapValues { (_, dates) ->
                dates.sumOf { d ->
                    DailySummaryCalculator.summarize(
                        stepsByDate[d]?.total ?: 0, activitiesByDate[d].orEmpty(), heightCm, latestWeight,
                    ).activeMinutes
                }
            }

        return AchievementStats(
            maxDailySteps = steps.maxOfOrNull { it.total } ?: 0,
            lifetimeSteps = steps.sumOf { it.total.toLong() },
            longestActivityStreak = StreakCalculator.longestStreak(goalDays + activityDays),
            weightEntryCount = weights.size,
            maxWeightChangeKg = maxChange,
            longestTrackingStreak = StreakCalculator.longestStreak(trackingDays),
            weeklyGoalCompleted = goals.weeklyActiveMinutesGoal > 0 &&
                minutesByWeek.values.any { it >= goals.weeklyActiveMinutesGoal },
            activityCount = activities.size,
        )
    }

    /** Helper for "this week" progress on screens. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
