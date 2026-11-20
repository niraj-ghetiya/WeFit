package com.wefit.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.calc.DailySummaryCalculator
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.ReminderRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.notifications.NotificationHelper
import com.wefit.app.step.StepTracker
import com.wefit.app.ui.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val steps: StepRepository,
    private val goals: GoalRepository,
    private val activities: ActivityRepository,
    private val weights: WeightRepository,
    private val profile: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val tracker: StepTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() }
            ?: return Result.success()
        val setting = reminders.get(type)
        if (!setting.enabled) return Result.success()
        runCatching { notify(type) }
        scheduler.schedule(setting, fromWorker = true)
        return Result.success()
    }

    private suspend fun notify(type: ReminderType) {
        val ctx = applicationContext
        val today = LocalDate.now()
        val id = 2000 + type.ordinal
        when (type) {
            ReminderType.WEIGHT -> NotificationHelper.show(
                ctx, id, NotificationHelper.CHANNEL_REMINDERS,
                "Time to weigh in", "Log today's weight to keep your progress chart up to date.", Routes.addWeight(),
            )
            ReminderType.STEP_GOAL -> {
                tracker.syncOnce()
                val goal = goals.get().dailyStepGoal
                val total = steps.getDay(today)?.total ?: 0
                if (total < goal) {
                    NotificationHelper.show(
                        ctx, id, NotificationHelper.CHANNEL_REMINDERS, "Keep moving!",
                        "You're at ${Formatters.number(total)} of ${Formatters.number(goal)} steps today. " +
                            "${Formatters.number(goal - total)} to go.",
                        Routes.TAB_ACTIVITY,
                    )
                }
            }
            ReminderType.ACTIVITY -> if (activities.getByDate(today).isEmpty()) {
                NotificationHelper.show(
                    ctx, id, NotificationHelper.CHANNEL_REMINDERS, "Log your activity",
                    "Did you walk, run or ride today? Add it to WeFit.", Routes.addActivity(),
                )
            }
            ReminderType.WEEKLY_SUMMARY -> {
                val from = today.minusDays(6)
                val days = steps.getAll().filter { it.date in from..today }
                val total = days.sumOf { it.total }
                val acts = activities.getAll().filter { it.date in from..today }
                val height = profile.get()?.heightCm
                val allWeights = weights.getAll()
                val latestKg = allWeights.firstOrNull()?.weightKg
                val minutes = (0L..6L).sumOf { i ->
                    val d = from.plusDays(i)
                    DailySummaryCalculator.summarize(
                        days.firstOrNull { it.date == d }?.total ?: 0, acts.filter { it.date == d }, height, latestKg,
                    ).activeMinutes
                }
                val inWeek = allWeights.filter { it.date in from..today }.sortedBy { it.date }
                val units = preferences.preferences.first().unitSystem
                val weightText = if (inWeek.size >= 2)
                    " Weight change: ${Formatters.weightChange(inWeek.last().weightKg - inWeek.first().weightKg, units)}." else ""
                NotificationHelper.show(
                    ctx, id, NotificationHelper.CHANNEL_REMINDERS, "Your week in WeFit",
                    "${Formatters.number(total)} steps (avg ${Formatters.number(total / 7)}/day), " +
                        "${Formatters.duration(minutes)} active.$weightText",
                    Routes.INSIGHTS,
                )
            }
        }
    }

    companion object { const val KEY_TYPE = "reminder_type" }
}
