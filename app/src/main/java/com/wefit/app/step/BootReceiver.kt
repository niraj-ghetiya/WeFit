package com.wefit.app.step

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wefit.app.di.ApplicationScope
import com.wefit.app.domain.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * After a reboot the hardware step counter restarts from zero; StepCounterLogic detects that.
 * Here we take a first post-boot reading and restart optional background tracking.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var tracker: StepTracker
    @Inject lateinit var preferences: PreferencesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                tracker.syncOnce()
                if (preferences.preferences.first().backgroundTracking) StepTrackingService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
