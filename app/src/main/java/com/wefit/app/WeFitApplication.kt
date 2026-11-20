package com.wefit.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.wefit.app.achievements.AchievementWatcher
import com.wefit.app.di.ApplicationScope
import com.wefit.app.notifications.NotificationHelper
import com.wefit.app.reminders.ReminderScheduler
import com.wefit.app.step.StepTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WeFitApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var stepTracker: StepTracker
    @Inject lateinit var achievementWatcher: AchievementWatcher
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        stepTracker.initialize()
        achievementWatcher.start()
        appScope.launch { runCatching { reminderScheduler.syncAll() } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = stepTracker.onAppForeground()
            override fun onStop(owner: LifecycleOwner) = stepTracker.onAppBackground()
        })
    }
}
