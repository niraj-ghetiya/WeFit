package com.wefit.app.step

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.wefit.app.domain.calc.StepCounterLogic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper around the hardware TYPE_STEP_COUNTER sensor. */
@Singleton
class StepSensorSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    private val sensor: Sensor? get() = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable: Boolean get() = sensor != null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Emits cumulative step counts since boot. Completes immediately if unavailable. */
    fun readings(): Flow<Long> = callbackFlow {
        val sm = sensorManager
        val s = sensor
        if (sm == null || s == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) trySend(event.values[0].toLong())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)) close()
        awaitClose { sm.unregisterListener(listener) }
    }

    suspend fun readOnce(timeoutMs: Long = 10_000): Long? = withTimeoutOrNull(timeoutMs) { readings().first() }

    fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, StepCounterLogic.UNKNOWN_BOOT_COUNT)

    fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
}
