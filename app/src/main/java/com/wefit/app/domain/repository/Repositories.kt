package com.wefit.app.domain.repository

import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.UnlockedAchievement
import com.wefit.app.domain.model.UserPreferences
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUnitSystem(units: UnitSystem)
    suspend fun setOnboardingCompleted(done: Boolean)
    suspend fun setSetupCompleted(done: Boolean)
    suspend fun setBackgroundTracking(enabled: Boolean)
    suspend fun clear()
}

interface ProfileRepository {
    val profile: Flow<UserProfile?>
    suspend fun get(): UserProfile?
    suspend fun save(profile: UserProfile)
}

interface WeightRepository {
    /** Newest first. */
    val entries: Flow<List<WeightEntry>>
    suspend fun get(id: Long): WeightEntry?
    suspend fun add(entry: WeightEntry): Long
    suspend fun update(entry: WeightEntry)
    suspend fun delete(id: Long)
    suspend fun getAll(): List<WeightEntry>
}

interface StepRepository {
    fun observeDay(date: LocalDate): Flow<DailySteps?>
    val allDays: Flow<List<DailySteps>>
    suspend fun getDay(date: LocalDate): DailySteps?
    suspend fun getAll(): List<DailySteps>
    /** Adds (or subtracts) manually entered steps for [date]. */
    suspend fun adjustManualSteps(date: LocalDate, delta: Int)
    /**
     * Processes a cumulative step-counter reading. Returns the number of steps newly credited.
     */
    suspend fun recordSensorReading(counterValue: Long, bootCount: Int, now: Long, bootTime: Long): Long
    /** Refreshes the goal snapshot stored on today's record. */
    suspend fun syncGoalForDay(date: LocalDate, goal: Int)
}

interface ActivityRepository {
    val all: Flow<List<ActivityEntry>>
    fun observeByDate(date: LocalDate): Flow<List<ActivityEntry>>
    suspend fun get(id: Long): ActivityEntry?
    suspend fun add(entry: ActivityEntry): Long
    suspend fun update(entry: ActivityEntry)
    suspend fun delete(id: Long)
    suspend fun getByDate(date: LocalDate): List<ActivityEntry>
    suspend fun getAll(): List<ActivityEntry>
}

interface GoalRepository {
    val goals: Flow<FitnessGoals>
    suspend fun get(): FitnessGoals
    suspend fun save(goals: FitnessGoals)
}

interface ReminderRepository {
    val reminders: Flow<List<ReminderSetting>>
    suspend fun get(type: ReminderType): ReminderSetting
    suspend fun getAll(): List<ReminderSetting>
    suspend fun save(setting: ReminderSetting)
}

interface AchievementRepository {
    val unlocked: Flow<List<UnlockedAchievement>>
    /** Persists unlocks; returns the ids that were not unlocked before. */
    suspend fun unlock(ids: Set<String>, at: Long): List<String>
}
