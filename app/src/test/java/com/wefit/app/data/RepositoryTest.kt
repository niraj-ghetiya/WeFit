package com.wefit.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.wefit.app.data.backup.BackupFile
import com.wefit.app.data.backup.DataTransferManager
import com.wefit.app.data.backup.InvalidBackupException
import com.wefit.app.data.local.WeFitDatabase
import com.wefit.app.data.repository.AchievementRepositoryImpl
import com.wefit.app.data.repository.ActivityRepositoryImpl
import com.wefit.app.data.repository.GoalRepositoryImpl
import com.wefit.app.data.repository.ProfileRepositoryImpl
import com.wefit.app.data.repository.ReminderRepositoryImpl
import com.wefit.app.data.repository.StepRepositoryImpl
import com.wefit.app.data.repository.WeightRepositoryImpl
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.UserPreferences
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

class FakePreferences : PreferencesRepository {
    val state = MutableStateFlow(UserPreferences())
    override val preferences = state
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setUnitSystem(units: UnitSystem) = state.update { it.copy(unitSystem = units) }
    override suspend fun setOnboardingCompleted(done: Boolean) = state.update { it.copy(onboardingCompleted = done) }
    override suspend fun setSetupCompleted(done: Boolean) = state.update { it.copy(setupCompleted = done) }
    override suspend fun setBackgroundTracking(enabled: Boolean) = state.update { it.copy(backgroundTracking = enabled) }
    override suspend fun clear() { state.value = UserPreferences() }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: WeFitDatabase
    private lateinit var steps: StepRepositoryImpl
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0, day: Int = 20) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 11, 20)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WeFitDatabase::class.java).allowMainThreadQueries().build()
        steps = StepRepositoryImpl(db, db.stepDao(), db.goalDao()).also { it.zone = zone }
    }

    @After fun tearDown() = db.close()

    @Test fun sensorReadingsAccumulateWithoutDuplicates() = runTest {
        val boot = at(6)
        steps.recordSensorReading(1_000, 3, at(8), boot)          // booted today -> credits 1,000
        steps.recordSensorReading(1_000, 3, at(8, 5), boot)       // duplicate reading -> +0
        steps.recordSensorReading(1_600, 3, at(8, 20), boot)      // +600
        val record = steps.getDay(day)!!
        assertThat(record.sensorSteps).isEqualTo(1_600)
        assertThat(record.hourly.sum()).isEqualTo(1_600)
        assertThat(steps.getAll()).hasSize(1)
    }

    @Test fun rebootKeepsEarlierStepsAndAddsNewOnes() = runTest {
        steps.recordSensorReading(500, 3, at(8), at(7))
        steps.recordSensorReading(2_500, 3, at(9), at(7))         // total 2,500
        steps.recordSensorReading(400, 4, at(11), at(10, 30))     // reboot -> +400
        assertThat(steps.getDay(day)!!.total).isEqualTo(2_900)
    }

    @Test fun readingsOnDifferentDaysGoToSeparateRecords() = runTest {
        steps.recordSensorReading(100, 1, at(22, day = 19), at(21, day = 19))
        steps.recordSensorReading(400, 1, at(23, 30, day = 19), at(21, day = 19))
        steps.recordSensorReading(1_000, 1, at(9), at(21, day = 19))  // long gap -> current hour
        assertThat(steps.getDay(day.minusDays(1))!!.total).isEqualTo(400)
        assertThat(steps.getDay(day)!!.total).isEqualTo(600)
    }

    @Test fun manualAdjustmentNeverMakesTotalNegative() = runTest {
        steps.recordSensorReading(300, 1, at(8), at(7, 30))
        steps.adjustManualSteps(day, 1_000)
        assertThat(steps.getDay(day)!!.total).isEqualTo(1_300)
        steps.adjustManualSteps(day, -5_000)
        val r = steps.getDay(day)!!
        assertThat(r.total).isEqualTo(0)
        assertThat(r.manualSteps).isEqualTo(-300)
        assertThat(r.sensorSteps).isEqualTo(300)
    }

    @Test fun goalSnapshotIsStoredOnRecords() = runTest {
        GoalRepositoryImpl(db.goalDao()).save(FitnessGoals(dailyStepGoal = 6_000))
        steps.adjustManualSteps(day, 6_500)
        assertThat(steps.getDay(day)!!.goalMet).isTrue()
        steps.syncGoalForDay(day, 8_000)
        assertThat(steps.getDay(day)!!.goalMet).isFalse()
    }

    @Test fun weightCrudAndOrdering() = runTest {
        val repo = WeightRepositoryImpl(db.weightDao())
        val older = repo.add(WeightEntry(weightKg = 75.0, date = day.minusDays(10)))
        val newer = repo.add(WeightEntry(weightKg = 74.2, date = day, note = "  "))
        assertThat(repo.entries.first().map { it.id }).containsExactly(newer, older).inOrder()
        assertThat(repo.get(newer)!!.note).isNull()
        repo.update(repo.get(older)!!.copy(weightKg = 76.0))
        assertThat(repo.get(older)!!.weightKg).isEqualTo(76.0)
        repo.delete(newer)
        assertThat(repo.getAll()).hasSize(1)
    }

    @Test fun activitiesByDate() = runTest {
        val repo = ActivityRepositoryImpl(db.activityDao())
        repo.add(ActivityEntry(type = ActivityType.RUNNING, date = day, durationMinutes = 30))
        repo.add(ActivityEntry(type = ActivityType.CYCLING, date = day.minusDays(1), durationMinutes = 45))
        assertThat(repo.observeByDate(day).first().map { it.type }).containsExactly(ActivityType.RUNNING)
    }

    @Test fun achievementsUnlockOnlyOnce() = runTest {
        val repo = AchievementRepositoryImpl(db.achievementDao())
        assertThat(repo.unlock(setOf("steps_1k", "first_weight"), 100L)).containsExactly("steps_1k", "first_weight")
        assertThat(repo.unlock(setOf("steps_1k", "steps_5k"), 200L)).containsExactly("steps_5k")
        val unlocked = repo.unlocked.first().associate { it.id to it.unlockedAt }
        assertThat(unlocked["steps_1k"]).isEqualTo(100L)
    }

    @Test fun remindersDefaultToDisabledForEveryType() = runTest {
        val repo = ReminderRepositoryImpl(db.reminderDao())
        val all = repo.getAll()
        assertThat(all.map { it.type }).containsExactlyElementsIn(ReminderType.entries)
        assertThat(all.none { it.enabled }).isTrue()
        repo.save(all.first().copy(enabled = true, hour = 7))
        assertThat(repo.get(all.first().type).hour).isEqualTo(7)
    }

    private fun transferManager(prefs: FakePreferences): DataTransferManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = ReminderScheduler(context, ReminderRepositoryImpl(db.reminderDao()))
        return DataTransferManager(context, db, prefs, scheduler)
    }

    @Test fun backupRoundTripRestoresEverything() = runTest {
        val prefs = FakePreferences().apply { setUnitSystem(UnitSystem.IMPERIAL) }
        val manager = transferManager(prefs)
        ProfileRepositoryImpl(db.profileDao()).save(UserProfile("Alex", 30, 175.0))
        WeightRepositoryImpl(db.weightDao()).add(WeightEntry(weightKg = 72.5, date = day, note = "Morning, after run"))
        steps.adjustManualSteps(day, 4_200)
        ActivityRepositoryImpl(db.activityDao()).add(ActivityEntry(type = ActivityType.WALKING, date = day, durationMinutes = 40, distanceKm = 3.1))
        AchievementRepositoryImpl(db.achievementDao()).unlock(setOf("steps_1k"), 5L)

        val json = manager.encode(manager.createBackup())
        manager.resetAll()
        assertThat(WeightRepositoryImpl(db.weightDao()).getAll()).isEmpty()
        assertThat(prefs.state.value.unitSystem).isEqualTo(UnitSystem.METRIC)

        val summary = manager.restore(manager.decode(json))
        assertThat(summary.weights).isEqualTo(1)
        assertThat(ProfileRepositoryImpl(db.profileDao()).get()!!.name).isEqualTo("Alex")
        assertThat(steps.getDay(day)!!.total).isEqualTo(4_200)
        assertThat(ActivityRepositoryImpl(db.activityDao()).getAll().single().distanceKm).isEqualTo(3.1)
        assertThat(db.achievementDao().getAll().single().unlockedAt).isEqualTo(5L)
        assertThat(prefs.state.value.unitSystem).isEqualTo(UnitSystem.IMPERIAL)
        assertThat(prefs.state.value.setupCompleted).isTrue()
    }

    @Test fun invalidBackupsAreRejected() {
        val manager = transferManager(FakePreferences())
        assertThrows(InvalidBackupException::class.java) { manager.decode("not json") }
        assertThrows(InvalidBackupException::class.java) { manager.decode("""{"app":"Other","exportedAt":1}""") }
        assertThrows(InvalidBackupException::class.java) {
            manager.decode("""{"app":"WeFit","formatVersion":${BackupFile.FORMAT_VERSION + 1},"exportedAt":1}""")
        }
    }

    @Test fun csvExportContainsAllDatasets() = runTest {
        val manager = transferManager(FakePreferences())
        WeightRepositoryImpl(db.weightDao()).add(WeightEntry(weightKg = 72.5, date = day, note = "a,b"))
        steps.adjustManualSteps(day, 1_234)
        val out = ByteArrayOutputStream()
        manager.writeCsvZip(out)
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e -> files[e.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertThat(files.keys).containsExactly("weights.csv", "daily_steps.csv", "activities.csv")
        assertThat(files["weights.csv"]).contains("2026-11-20,72.50,\"a,b\"")
        assertThat(files["daily_steps.csv"]).contains("2026-11-20,1234,0,1234,10000")
    }
}
