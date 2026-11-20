package com.wefit.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String,
    val age: Int?,
    val heightCm: Double?,
    val updatedAt: Long,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Serializable
@Entity(tableName = "weight_entries", indices = [Index("epochDay")])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Double,
    val epochDay: Long,
    val note: String?,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "daily_steps")
data class DailyStepRecordEntity(
    /** One row per local calendar day, which makes duplicate daily records impossible. */
    @PrimaryKey val epochDay: Long,
    val sensorSteps: Int,
    val manualSteps: Int,
    /** 24 comma-separated hourly sensor step counts. */
    val hourly: List<Int>,
    val goal: Int,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "activities", indices = [Index("epochDay")])
data class ActivityEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val epochDay: Long,
    val durationMinutes: Int,
    val distanceKm: Double?,
    val caloriesKcal: Int?,
    val note: String?,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "fitness_goals")
data class FitnessGoalEntity(
    @PrimaryKey val id: Int = 1,
    val dailyStepGoal: Int,
    val targetWeightKg: Double?,
    val weeklyActiveMinutesGoal: Int,
)

@Serializable
@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey val type: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    /** ISO day of week 1 (Monday) .. 7 (Sunday); used by weekly reminders. */
    val dayOfWeek: Int,
)

@Serializable
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)

/** Last processed step-counter reading (single row). */
@Serializable
@Entity(tableName = "step_sensor_state")
data class StepSensorStateEntity(
    @PrimaryKey val id: Int = 1,
    val counterValue: Long,
    val bootCount: Int,
    val timestamp: Long,
)
