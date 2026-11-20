package com.wefit.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class UnitSystem { METRIC, IMPERIAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ActivityType(val label: String, val met: Double, val isStepBased: Boolean) {
    WALKING("Walking", 3.5, true),
    RUNNING("Running", 9.8, true),
    CYCLING("Cycling", 7.5, false),
    OTHER("Other", 4.0, false),
}

enum class ReminderType(val title: String, val description: String, val defaultHour: Int, val defaultMinute: Int, val weekly: Boolean) {
    WEIGHT("Weight Reminder", "Log your weight", 8, 0, false),
    STEP_GOAL("Step Goal Reminder", "Nudge if today's step goal isn't reached yet", 18, 0, false),
    ACTIVITY("Activity Reminder", "Log a workout if you haven't today", 18, 30, false),
    WEEKLY_SUMMARY("Weekly Progress Summary", "Your last 7 days at a glance", 19, 0, true),
}

data class UserProfile(
    val name: String = "",
    val age: Int? = null,
    val heightCm: Double? = null,
)

data class WeightEntry(
    val id: Long = 0,
    val weightKg: Double,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DailySteps(
    val date: LocalDate,
    val sensorSteps: Int = 0,
    val manualSteps: Int = 0,
    val hourly: List<Int> = List(24) { 0 },
    val goal: Int = FitnessGoals.DEFAULT_STEP_GOAL,
) {
    /** Sensor steps plus manual adjustments; never negative. */
    val total: Int get() = (sensorSteps + manualSteps).coerceAtLeast(0)
    val goalMet: Boolean get() = goal > 0 && total >= goal
}

data class ActivityEntry(
    val id: Long = 0,
    val type: ActivityType,
    val date: LocalDate,
    val durationMinutes: Int,
    val distanceKm: Double? = null,
    val caloriesKcal: Int? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class FitnessGoals(
    val dailyStepGoal: Int = DEFAULT_STEP_GOAL,
    val targetWeightKg: Double? = null,
    val weeklyActiveMinutesGoal: Int = DEFAULT_WEEKLY_MINUTES,
) {
    companion object {
        const val DEFAULT_STEP_GOAL = 10_000
        const val DEFAULT_WEEKLY_MINUTES = 150
    }
}

data class ReminderSetting(
    val type: ReminderType,
    val enabled: Boolean = false,
    val hour: Int = type.defaultHour,
    val minute: Int = type.defaultMinute,
    val dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
)

data class UnlockedAchievement(val id: String, val unlockedAt: Long)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val onboardingCompleted: Boolean = false,
    val setupCompleted: Boolean = false,
    val backgroundTracking: Boolean = false,
)
