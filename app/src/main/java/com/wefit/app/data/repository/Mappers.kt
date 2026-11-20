package com.wefit.app.data.repository

import com.wefit.app.data.local.ActivityEntryEntity
import com.wefit.app.data.local.DailyStepRecordEntity
import com.wefit.app.data.local.FitnessGoalEntity
import com.wefit.app.data.local.ReminderSettingsEntity
import com.wefit.app.data.local.UserProfileEntity
import com.wefit.app.data.local.WeightEntryEntity
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.model.WeightEntry
import java.time.DayOfWeek
import java.time.LocalDate

fun UserProfileEntity.toDomain() = UserProfile(name = name, age = age, heightCm = heightCm)
fun UserProfile.toEntity(now: Long = System.currentTimeMillis()) =
    UserProfileEntity(name = name.trim(), age = age, heightCm = heightCm, updatedAt = now)

fun WeightEntryEntity.toDomain() = WeightEntry(id, weightKg, LocalDate.ofEpochDay(epochDay), note, createdAt)
fun WeightEntry.toEntity() = WeightEntryEntity(id, weightKg, date.toEpochDay(), note?.trim()?.ifBlank { null }, createdAt)

fun DailyStepRecordEntity.toDomain() = DailySteps(LocalDate.ofEpochDay(epochDay), sensorSteps, manualSteps, hourly, goal)
fun DailySteps.toEntity(now: Long = System.currentTimeMillis()) =
    DailyStepRecordEntity(date.toEpochDay(), sensorSteps, manualSteps, hourly, goal, now)

fun ActivityEntryEntity.toDomain() = ActivityEntry(
    id = id,
    type = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
    date = LocalDate.ofEpochDay(epochDay),
    durationMinutes = durationMinutes,
    distanceKm = distanceKm,
    caloriesKcal = caloriesKcal,
    note = note,
    createdAt = createdAt,
)
fun ActivityEntry.toEntity() = ActivityEntryEntity(
    id, type.name, date.toEpochDay(), durationMinutes, distanceKm, caloriesKcal, note?.trim()?.ifBlank { null }, createdAt,
)

fun FitnessGoalEntity?.toDomain() = if (this == null) FitnessGoals() else
    FitnessGoals(dailyStepGoal, targetWeightKg, weeklyActiveMinutesGoal)
fun FitnessGoals.toEntity() = FitnessGoalEntity(1, dailyStepGoal, targetWeightKg, weeklyActiveMinutesGoal)

fun ReminderSettingsEntity.toDomain(): ReminderSetting? {
    val t = runCatching { ReminderType.valueOf(type) }.getOrNull() ?: return null
    return ReminderSetting(t, enabled, hour, minute, DayOfWeek.of(dayOfWeek.coerceIn(1, 7)))
}
fun ReminderSetting.toEntity() = ReminderSettingsEntity(type.name, enabled, hour, minute, dayOfWeek.value)
