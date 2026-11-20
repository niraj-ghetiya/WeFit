package com.wefit.app.step

import android.content.Context
import android.util.Log
import com.wefit.app.di.ApplicationScope
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.StepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingStatus(
    val sensorAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val liveListening: Boolean = false,
) {
    val canTrack: Boolean get() = sensorAvailable && permissionGranted
}

/**
 * Coordinates step counting:
 *  - while the app is visible, a live sensor listener records steps in real time;
 *  - a periodic WorkManager job reconciles the cumulative counter every ~15 minutes;
 *  - optionally (user opt-in) a health foreground service keeps listening in the background.
 * Because the hardware counter is cumulative since boot, steps taken while WeFit is closed are
 * still credited at the next reading, except steps taken before a reboot that was never sampled.
 */
@Singleton
class StepTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: StepSensorSource,
    private val stepRepository: StepRepository,
    private val preferences: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(TrackingStatus())
    val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    private var liveJob: Job? = null
    private var foreground = false

    fun refreshStatus() {
        _status.value = TrackingStatus(
            sensorAvailable = source.isAvailable,
            permissionGranted = source.hasPermission(),
            liveListening = liveJob?.isActive == true,
        )
    }

    suspend fun record(counterValue: Long): Long = runCatching {
        stepRepository.recordSensorReading(counterValue, source.bootCount(), System.currentTimeMillis(), source.bootTime())
    }.onFailure { Log.w(TAG, "Failed to record step reading", it) }.getOrDefault(0L)

    /** One-shot reconciliation; returns false when no reading was available. */
    suspend fun syncOnce(): Boolean {
        if (!source.isAvailable || !source.hasPermission()) return false
        val value = source.readOnce() ?: return false
        record(value)
        return true
    }

    fun onAppForeground() {
        foreground = true
        refreshStatus()
        startLive()
    }

    fun onAppBackground() {
        foreground = false
        stopLive()
    }

    /** Call after the activity-recognition permission may have changed. */
    fun onPermissionResult() {
        refreshStatus()
        if (!_status.value.canTrack) return
        StepSyncWorker.schedule(context)
        if (foreground) startLive()
        scope.launch {
            if (preferences.preferences.first().backgroundTracking) StepTrackingService.start(context)
        }
    }

    /** Starts background work on app launch when tracking is possible. */
    fun initialize() {
        refreshStatus()
        if (!_status.value.canTrack) return
        StepSyncWorker.schedule(context)
        scope.launch {
            if (preferences.preferences.first().backgroundTracking) StepTrackingService.start(context)
        }
    }

    private fun startLive() {
        if (liveJob?.isActive == true || !source.isAvailable || !source.hasPermission()) return
        liveJob = scope.launch {
            source.readings().collect { record(it) }
        }
        refreshStatus()
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        refreshStatus()
    }

    companion object { private const val TAG = "StepTracker" }
}
