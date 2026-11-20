package com.wefit.app.step

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.wefit.app.R
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Optional, user-enabled foreground service (type "health") that keeps the step sensor listener
 * registered while WeFit is in the background. It shows a persistent notification as Android requires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class StepTrackingService : LifecycleService() {

    @Inject lateinit var source: StepSensorSource
    @Inject lateinit var tracker: StepTracker
    @Inject lateinit var stepRepository: StepRepository

    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        try {
            ServiceCompat.startForeground(this, NotificationHelper.TRACKING_NOTIFICATION_ID, buildNotification(null), type)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground tracking", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!source.isAvailable || !source.hasPermission()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            lifecycleScope.launch { source.readings().collect { tracker.record(it) } }
            lifecycleScope.launch {
                // Re-subscribe when the date changes so the notification follows midnight.
                dates().distinctUntilChanged()
                    .flatMapLatest { date -> stepRepository.observeDay(date) }
                    .collect { updateNotification(it?.total ?: 0) }
            }
        }
        return START_STICKY
    }

    private fun dates() = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }

    @Suppress("MissingPermission")
    private fun updateNotification(steps: Int) {
        if (!NotificationHelper.canPostNotifications(this)) return
        runCatching { NotificationManagerCompat.from(this).notify(NotificationHelper.TRACKING_NOTIFICATION_ID, buildNotification(steps)) }
    }

    private fun buildNotification(steps: Int?): Notification =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C3AED.toInt())
            .setContentTitle(if (steps == null) "Counting your steps" else "${Formatters.number(steps)} steps today")
            .setContentText("WeFit background step tracking is on")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(NotificationHelper.contentIntent(this, null, 0))
            .build()

    companion object {
        private const val TAG = "StepTrackingService"

        fun start(context: Context) {
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasSensor = context.getSystemService(android.hardware.SensorManager::class.java)
                ?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
            if (!granted || !hasSensor) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, StepTrackingService::class.java)) }
                .onFailure { Log.w(TAG, "Unable to start tracking service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepTrackingService::class.java))
        }
    }
}
