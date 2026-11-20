package com.wefit.app.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wefit.app.domain.calc.ReminderTimeCalculator
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminders with WorkManager. Each reminder is a one-time job that reschedules itself
 * after it runs, which keeps the time of day stable. Delivery is inexact: Android may delay work
 * under Doze / battery optimisation, which is explained in the Reminders screen.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule(setting: ReminderSetting, fromWorker: Boolean = false) {
        val name = workName(setting.type)
        if (!setting.enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, ReminderTimeCalculator.nextTrigger(setting, now)).toMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to setting.type.name))
            .addTag(TAG)
            .build()
        // From inside the running worker, append so we don't cancel ourselves.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(name, policy, request)
    }

    suspend fun syncAll() {
        repository.getAll().forEach { schedule(it) }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
        ReminderType.entries.forEach { workManager.cancelUniqueWork(workName(it)) }
    }

    companion object {
        private const val TAG = "wefit_reminder"
        fun workName(type: ReminderType) = "reminder_${type.name.lowercase()}"
    }
}
