package com.wefit.app.domain.calc

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Last processed reading of the cumulative TYPE_STEP_COUNTER sensor. */
data class SensorSnapshot(val counterValue: Long, val bootCount: Int, val timestamp: Long)

data class StepDelta(
    /** New steps to credit (never negative). */
    val steps: Long,
    /** Start of the interval the steps were taken in (used for hourly allocation). */
    val intervalStart: Long,
    val snapshot: SensorSnapshot,
    val rebootDetected: Boolean,
)

/**
 * Turns cumulative step-counter readings into per-hour step deltas.
 *
 * TYPE_STEP_COUNTER reports the number of steps since the last reboot. We persist the last value we
 * saw; every new reading credits only the difference, so repeated readings or app restarts never
 * double count. A reboot is detected when Settings.Global.BOOT_COUNT changes or the counter goes
 * backwards; in that case the steps since boot are credited.
 */
object StepCounterLogic {
    const val UNKNOWN_BOOT_COUNT = -1
    /** Upper bound used to reject corrupt sensor values (well above elite running cadence). */
    const val MAX_STEPS_PER_MINUTE = 250L
    /** Steps from intervals longer than this are credited to the current hour instead of being spread. */
    const val MAX_SPREAD_MILLIS = 3 * 60 * 60 * 1000L

    fun computeDelta(
        previous: SensorSnapshot?,
        counterValue: Long,
        bootCount: Int,
        now: Long,
        bootTime: Long,
        startOfToday: Long,
    ): StepDelta {
        val snapshot = SensorSnapshot(counterValue.coerceAtLeast(0), bootCount, now)
        if (previous == null) {
            // First reading ever. Steps since boot can only be attributed if the device booted today.
            return if (bootTime >= startOfToday) {
                StepDelta(capped(counterValue, bootTime, now), bootTime, snapshot, false)
            } else {
                StepDelta(0, now, snapshot, false)
            }
        }
        val bootChanged = bootCount != UNKNOWN_BOOT_COUNT &&
            previous.bootCount != UNKNOWN_BOOT_COUNT &&
            bootCount != previous.bootCount
        val rebooted = bootChanged || counterValue < previous.counterValue
        return if (rebooted) {
            val start = max(bootTime, previous.timestamp)
            StepDelta(capped(counterValue, start, now), start, snapshot, true)
        } else {
            StepDelta(capped(counterValue - previous.counterValue, previous.timestamp, now), previous.timestamp, snapshot, false)
        }
    }

    private fun capped(steps: Long, from: Long, to: Long): Long {
        val minutes = max(1L, ceil((to - from) / 60_000.0).toLong())
        return steps.coerceIn(0L, minutes * MAX_STEPS_PER_MINUTE)
    }

    /**
     * Spreads [steps] over the hour buckets between [start] and [end] in proportion to time.
     * The returned map always sums exactly to [steps].
     */
    fun allocate(steps: Long, start: Long, end: Long, zone: ZoneId): Map<Pair<LocalDate, Int>, Long> {
        if (steps <= 0) return emptyMap()
        val endZoned = Instant.ofEpochMilli(end).atZone(zone)
        val endBucket = endZoned.toLocalDate() to endZoned.hour
        val span = end - start
        if (span <= 0 || span > MAX_SPREAD_MILLIS) return mapOf(endBucket to steps)

        val weights = LinkedHashMap<Pair<LocalDate, Int>, Long>()
        var cursor = start
        while (cursor < end) {
            val z = Instant.ofEpochMilli(cursor).atZone(zone)
            val bucketEnd = z.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val segEnd = min(bucketEnd, end)
            val key = z.toLocalDate() to z.hour
            weights[key] = (weights[key] ?: 0L) + (segEnd - cursor)
            cursor = segEnd
        }
        val result = LinkedHashMap<Pair<LocalDate, Int>, Long>()
        var assigned = 0L
        weights.forEach { (k, w) ->
            val share = steps * w / span
            result[k] = share
            assigned += share
        }
        val remainder = steps - assigned
        if (remainder > 0) result[endBucket] = (result[endBucket] ?: 0L) + remainder
        return result.filterValues { it > 0 }
    }
}
