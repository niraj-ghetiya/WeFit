package com.wefit.app.data.repository

import androidx.room.withTransaction
import com.wefit.app.data.local.AchievementDao
import com.wefit.app.data.local.AchievementEntity
import com.wefit.app.data.local.ActivityDao
import com.wefit.app.data.local.DailyStepRecordEntity
import com.wefit.app.data.local.GoalDao
import com.wefit.app.data.local.ProfileDao
import com.wefit.app.data.local.ReminderDao
import com.wefit.app.data.local.StepDao
import com.wefit.app.data.local.StepSensorStateEntity
import com.wefit.app.data.local.WeFitDatabase
import com.wefit.app.data.local.WeightDao
import com.wefit.app.domain.calc.SensorSnapshot
import com.wefit.app.domain.calc.StepCounterLogic
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.model.UnlockedAchievement
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.domain.repository.AchievementRepository
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.ReminderRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(private val dao: ProfileDao) : ProfileRepository {
    override val profile: Flow<UserProfile?> = dao.observe().map { it?.toDomain() }
    override suspend fun get(): UserProfile? = dao.get()?.toDomain()
    override suspend fun save(profile: UserProfile) = dao.upsert(profile.toEntity())
}

@Singleton
class WeightRepositoryImpl @Inject constructor(private val dao: WeightDao) : WeightRepository {
    override val entries: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun get(id: Long) = dao.get(id)?.toDomain()
    override suspend fun add(entry: WeightEntry): Long = dao.insert(entry.copy(id = 0).toEntity())
    override suspend fun update(entry: WeightEntry) = dao.update(entry.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun getAll() = dao.getAll().map { it.toDomain() }
}

@Singleton
class StepRepositoryImpl @Inject constructor(
    private val db: WeFitDatabase,
    private val dao: StepDao,
    private val goalDao: GoalDao,
) : StepRepository {
    private val mutex = Mutex()
    internal var zone: ZoneId = ZoneId.systemDefault()

    override fun observeDay(date: LocalDate): Flow<DailySteps?> =
        dao.observeDay(date.toEpochDay()).map { it?.toDomain() }.distinctUntilChanged()

    override val allDays: Flow<List<DailySteps>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun getDay(date: LocalDate) = dao.getDay(date.toEpochDay())?.toDomain()
    override suspend fun getAll() = dao.getAll().map { it.toDomain() }

    private suspend fun currentGoal() = goalDao.get().toDomain().dailyStepGoal

    private fun emptyRecord(epochDay: Long, goal: Int, now: Long) =
        DailyStepRecordEntity(epochDay, 0, 0, List(24) { 0 }, goal, now)

    override suspend fun adjustManualSteps(date: LocalDate, delta: Int) = mutex.withLock {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val day = date.toEpochDay()
            val existing = dao.getDay(day) ?: emptyRecord(day, currentGoal(), now)
            // Never allow the manual adjustment to push the daily total below zero.
            val newManual = (existing.manualSteps + delta).coerceAtLeast(-existing.sensorSteps)
            dao.upsert(existing.copy(manualSteps = newManual, updatedAt = now))
        }
    }

    override suspend fun recordSensorReading(counterValue: Long, bootCount: Int, now: Long, bootTime: Long): Long =
        mutex.withLock {
            db.withTransaction {
                val prev = dao.getSensorState()?.let { SensorSnapshot(it.counterValue, it.bootCount, it.timestamp) }
                val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                val delta = StepCounterLogic.computeDelta(prev, counterValue, bootCount, now, bootTime, startOfToday)
                if (delta.steps > 0) {
                    val goal = currentGoal()
                    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    StepCounterLogic.allocate(delta.steps, delta.intervalStart, now, zone)
                        .entries.groupBy({ it.key.first }, { it.key.second to it.value })
                        .forEach { (date, hours) ->
                            val day = date.toEpochDay()
                            val existing = dao.getDay(day) ?: emptyRecord(day, goal, now)
                            val hourly = existing.hourly.toMutableList()
                            var added = 0
                            hours.forEach { (h, steps) -> hourly[h] += steps.toInt(); added += steps.toInt() }
                            dao.upsert(
                                existing.copy(
                                    sensorSteps = existing.sensorSteps + added,
                                    hourly = hourly,
                                    goal = if (date == today) goal else existing.goal,
                                    updatedAt = now,
                                ),
                            )
                        }
                }
                dao.saveSensorState(
                    StepSensorStateEntity(1, delta.snapshot.counterValue, delta.snapshot.bootCount, delta.snapshot.timestamp),
                )
                delta.steps
            }
        }

    override suspend fun syncGoalForDay(date: LocalDate, goal: Int) = mutex.withLock {
        val day = date.toEpochDay()
        val existing = dao.getDay(day)
        if (existing != null && existing.goal != goal) dao.upsert(existing.copy(goal = goal))
    }
}

@Singleton
class ActivityRepositoryImpl @Inject constructor(private val dao: ActivityDao) : ActivityRepository {
    override val all: Flow<List<ActivityEntry>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeByDate(date: LocalDate) = dao.observeByDay(date.toEpochDay()).map { l -> l.map { it.toDomain() } }
    override suspend fun get(id: Long) = dao.get(id)?.toDomain()
    override suspend fun add(entry: ActivityEntry) = dao.insert(entry.copy(id = 0).toEntity())
    override suspend fun update(entry: ActivityEntry) = dao.update(entry.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun getByDate(date: LocalDate) = dao.getByDay(date.toEpochDay()).map { it.toDomain() }
    override suspend fun getAll() = dao.getAll().map { it.toDomain() }
}

@Singleton
class GoalRepositoryImpl @Inject constructor(private val dao: GoalDao) : GoalRepository {
    override val goals: Flow<FitnessGoals> = dao.observe().map { it.toDomain() }
    override suspend fun get() = dao.get().toDomain()
    override suspend fun save(goals: FitnessGoals) = dao.upsert(goals.toEntity())
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(private val dao: ReminderDao) : ReminderRepository {
    private fun merge(stored: List<ReminderSetting>) =
        ReminderType.entries.map { t -> stored.firstOrNull { it.type == t } ?: ReminderSetting(t) }

    override val reminders: Flow<List<ReminderSetting>> =
        dao.observeAll().map { l -> merge(l.mapNotNull { it.toDomain() }) }

    override suspend fun get(type: ReminderType) = dao.get(type.name)?.toDomain() ?: ReminderSetting(type)
    override suspend fun getAll() = merge(dao.getAll().mapNotNull { it.toDomain() })
    override suspend fun save(setting: ReminderSetting) = dao.upsert(setting.toEntity())
}

@Singleton
class AchievementRepositoryImpl @Inject constructor(private val dao: AchievementDao) : AchievementRepository {
    override val unlocked: Flow<List<UnlockedAchievement>> =
        dao.observeAll().map { l -> l.map { UnlockedAchievement(it.id, it.unlockedAt) } }

    override suspend fun unlock(ids: Set<String>, at: Long): List<String> {
        if (ids.isEmpty()) return emptyList()
        val ordered = ids.toList()
        val results = dao.insertIgnore(ordered.map { AchievementEntity(it, at) })
        return ordered.filterIndexed { i, _ -> results.getOrElse(i) { -1L } != -1L }
    }
}
