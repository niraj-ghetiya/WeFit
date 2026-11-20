package com.wefit.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wefit.app.core.util.Csv
import com.wefit.app.data.local.AchievementEntity
import com.wefit.app.data.local.ActivityEntryEntity
import com.wefit.app.data.local.DailyStepRecordEntity
import com.wefit.app.data.local.FitnessGoalEntity
import com.wefit.app.data.local.ReminderSettingsEntity
import com.wefit.app.data.local.UserProfileEntity
import com.wefit.app.data.local.WeFitDatabase
import com.wefit.app.data.local.WeightEntryEntity
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.reminders.ReminderScheduler
import com.wefit.app.step.StepSyncWorker
import com.wefit.app.step.StepTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val unitSystem: String? = null,
    val themeMode: String? = null,
    val profile: UserProfileEntity? = null,
    val weights: List<WeightEntryEntity> = emptyList(),
    val steps: List<DailyStepRecordEntity> = emptyList(),
    val activities: List<ActivityEntryEntity> = emptyList(),
    val goals: FitnessGoalEntity? = null,
    val reminders: List<ReminderSettingsEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
) {
    companion object {
        const val APP_ID = "WeFit"
        const val FORMAT_VERSION = 1
    }
}

data class RestoreSummary(val weights: Int, val days: Int, val activities: Int)

class InvalidBackupException(message: String) : Exception(message)

/** Local-only export, backup, restore and reset. Nothing leaves the device unless the user shares the file. */
@Singleton
class DataTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeFitDatabase,
    private val preferences: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun createBackup(): BackupFile {
        val prefs = preferences.preferences.first()
        return BackupFile(
            exportedAt = System.currentTimeMillis(),
            unitSystem = prefs.unitSystem.name,
            themeMode = prefs.themeMode.name,
            profile = db.profileDao().get(),
            weights = db.weightDao().getAll(),
            steps = db.stepDao().getAll(),
            activities = db.activityDao().getAll(),
            goals = db.goalDao().get(),
            reminders = db.reminderDao().getAll(),
            achievements = db.achievementDao().getAll(),
        )
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("This file is not a valid WeFit backup.")
        }
        validate(backup)
        return backup
    }

    private fun validate(b: BackupFile) {
        if (b.app != BackupFile.APP_ID) throw InvalidBackupException("This file is not a WeFit backup.")
        if (b.formatVersion > BackupFile.FORMAT_VERSION) throw InvalidBackupException("This backup was made by a newer version of WeFit.")
        if (b.weights.any { it.weightKg !in 10.0..500.0 }) throw InvalidBackupException("Backup contains invalid weight values.")
        if (b.steps.any { it.sensorSteps < 0 || it.hourly.size != 24 }) throw InvalidBackupException("Backup contains invalid step records.")
        if (b.steps.map { it.epochDay }.toSet().size != b.steps.size) throw InvalidBackupException("Backup contains duplicate step days.")
        if (b.activities.any { it.durationMinutes < 0 || runCatching { ActivityType.valueOf(it.type) }.isFailure }) {
            throw InvalidBackupException("Backup contains invalid activities.")
        }
    }

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(createBackup())
        openOut(uri).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun restoreBackup(uri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val text = openIn(uri).use { it.readBytes().toString(Charsets.UTF_8) }
        restore(decode(text))
    }

    /** Replaces all tracked data with the backup contents in a single transaction. */
    suspend fun restore(backup: BackupFile): RestoreSummary {
        db.withTransaction {
            clearTables(keepSensorState = true)
            backup.profile?.let { db.profileDao().upsert(it) }
            db.weightDao().insertAll(backup.weights)
            db.stepDao().upsertAll(backup.steps)
            db.activityDao().insertAll(backup.activities)
            backup.goals?.let { db.goalDao().upsert(it) }
            db.reminderDao().upsertAll(backup.reminders)
            db.achievementDao().insertIgnore(backup.achievements)
        }
        backup.unitSystem?.let { u -> runCatching { UnitSystem.valueOf(u) }.getOrNull()?.let { preferences.setUnitSystem(it) } }
        backup.themeMode?.let { t -> runCatching { ThemeMode.valueOf(t) }.getOrNull()?.let { preferences.setThemeMode(it) } }
        preferences.setOnboardingCompleted(true)
        preferences.setSetupCompleted(true)
        runCatching { reminderScheduler.syncAll() }
        return RestoreSummary(backup.weights.size, backup.steps.size, backup.activities.size)
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        openOut(uri).use { writeCsvZip(it) }
    }

    suspend fun writeCsvZip(out: OutputStream) {
        val weights = db.weightDao().getAll().sortedBy { it.epochDay }
        val steps = db.stepDao().getAll()
        val activities = db.activityDao().getAll().sortedBy { it.epochDay }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, lines: List<String>) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("weights.csv", listOf(Csv.row("date", "weight_kg", "note")) +
                weights.map { Csv.row(LocalDate.ofEpochDay(it.epochDay), "%.2f".format(java.util.Locale.US, it.weightKg), it.note) })
            entry("daily_steps.csv", listOf(Csv.row("date", "total_steps", "sensor_steps", "manual_steps", "step_goal")) +
                steps.map { Csv.row(LocalDate.ofEpochDay(it.epochDay), (it.sensorSteps + it.manualSteps).coerceAtLeast(0), it.sensorSteps, it.manualSteps, it.goal) })
            entry("activities.csv", listOf(Csv.row("date", "type", "duration_min", "distance_km", "calories_kcal", "note")) +
                activities.map { Csv.row(LocalDate.ofEpochDay(it.epochDay), it.type, it.durationMinutes, it.distanceKm, it.caloriesKcal, it.note) })
        }
    }

    /** Wipes every table, all preferences and scheduled work. */
    suspend fun resetAll() {
        db.withTransaction { clearTables(keepSensorState = false) }
        preferences.clear()
        reminderScheduler.cancelAll()
        StepTrackingService.stop(context)
        StepSyncWorker.cancel(context)
    }

    private suspend fun clearTables(keepSensorState: Boolean) {
        val m = db.maintenanceDao()
        m.clearProfile(); m.clearWeights(); m.clearSteps(); m.clearActivities()
        m.clearGoals(); m.clearReminders(); m.clearAchievements()
        if (!keepSensorState) m.clearSensorState()
    }

    private fun openOut(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Unable to open file for writing")

    private fun openIn(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Unable to open file")
}
