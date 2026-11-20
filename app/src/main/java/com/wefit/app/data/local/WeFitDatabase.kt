package com.wefit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
        DailyStepRecordEntity::class,
        ActivityEntryEntity::class,
        FitnessGoalEntity::class,
        ReminderSettingsEntity::class,
        AchievementEntity::class,
        StepSensorStateEntity::class,
    ],
    version = WeFitDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WeFitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun stepDao(): StepDao
    abstract fun activityDao(): ActivityDao
    abstract fun goalDao(): GoalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun achievementDao(): AchievementDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val VERSION = 1
        const val NAME = "wefit.db"
    }
}

/**
 * Explicit schema migrations. Schemas are exported to app/schemas so each new version can be
 * verified with MigrationTestHelper. Destructive fallback is intentionally NOT enabled: user data
 * must survive upgrades.
 *
 * To add a migration: bump [WeFitDatabase.VERSION], add `Migration(n, n + 1) { db -> ... }` here.
 */
object WeFitMigrations {
    val ALL: Array<Migration> = arrayOf()
}
