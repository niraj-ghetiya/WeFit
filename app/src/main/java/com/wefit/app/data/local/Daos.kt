package com.wefit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC, createdAt DESC")
    suspend fun getAll(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE id = :id")
    suspend fun get(id: Long): WeightEntryEntity?

    @Insert
    suspend fun insert(entity: WeightEntryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<WeightEntryEntity>)

    @Update
    suspend fun update(entity: WeightEntryEntity)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StepDao {
    @Query("SELECT * FROM daily_steps WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DailyStepRecordEntity?>

    @Query("SELECT * FROM daily_steps WHERE epochDay = :epochDay")
    suspend fun getDay(epochDay: Long): DailyStepRecordEntity?

    @Query("SELECT * FROM daily_steps ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<DailyStepRecordEntity>>

    @Query("SELECT * FROM daily_steps ORDER BY epochDay ASC")
    suspend fun getAll(): List<DailyStepRecordEntity>

    @Upsert
    suspend fun upsert(entity: DailyStepRecordEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DailyStepRecordEntity>)

    @Query("SELECT * FROM step_sensor_state WHERE id = 1")
    suspend fun getSensorState(): StepSensorStateEntity?

    @Upsert
    suspend fun saveSensorState(entity: StepSensorStateEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<ActivityEntryEntity>>

    @Query("SELECT * FROM activities ORDER BY epochDay DESC, createdAt DESC")
    suspend fun getAll(): List<ActivityEntryEntity>

    @Query("SELECT * FROM activities WHERE epochDay = :epochDay ORDER BY createdAt DESC")
    fun observeByDay(epochDay: Long): Flow<List<ActivityEntryEntity>>

    @Query("SELECT * FROM activities WHERE epochDay = :epochDay ORDER BY createdAt DESC")
    suspend fun getByDay(epochDay: Long): List<ActivityEntryEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntryEntity?

    @Insert
    suspend fun insert(entity: ActivityEntryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ActivityEntryEntity>)

    @Update
    suspend fun update(entity: ActivityEntryEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM fitness_goals WHERE id = 1")
    fun observe(): Flow<FitnessGoalEntity?>

    @Query("SELECT * FROM fitness_goals WHERE id = 1")
    suspend fun get(): FitnessGoalEntity?

    @Upsert
    suspend fun upsert(entity: FitnessGoalEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_settings")
    fun observeAll(): Flow<List<ReminderSettingsEntity>>

    @Query("SELECT * FROM reminder_settings")
    suspend fun getAll(): List<ReminderSettingsEntity>

    @Query("SELECT * FROM reminder_settings WHERE type = :type")
    suspend fun get(type: String): ReminderSettingsEntity?

    @Upsert
    suspend fun upsert(entity: ReminderSettingsEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ReminderSettingsEntity>)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>

    /** Returns -1 for rows that already existed, so unlock times are never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<AchievementEntity>): List<Long>
}

/** Bulk deletes used by reset and backup restore. */
@Dao
interface MaintenanceDao {
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM weight_entries") suspend fun clearWeights()
    @Query("DELETE FROM daily_steps") suspend fun clearSteps()
    @Query("DELETE FROM activities") suspend fun clearActivities()
    @Query("DELETE FROM fitness_goals") suspend fun clearGoals()
    @Query("DELETE FROM reminder_settings") suspend fun clearReminders()
    @Query("DELETE FROM achievements") suspend fun clearAchievements()
    @Query("DELETE FROM step_sensor_state") suspend fun clearSensorState()
}
