package com.wefit.app.domain.calc

import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.WeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

enum class InsightsRange(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class ChartBar(val label: String, val value: Float, val highlighted: Boolean = false)

data class InsightsData(
    val range: InsightsRange,
    val start: LocalDate,
    val end: LocalDate,
    val title: String,
    val bars: List<ChartBar>,
    val totalSteps: Long,
    val avgDailySteps: Int,
    val previousAvgDailySteps: Int?,
    val totalDistanceKm: Double,
    val totalCalories: Int,
    val totalActiveMinutes: Int,
    val goalDaysMet: Int,
    val goalDaysCounted: Int,
    val weightPoints: List<Pair<LocalDate, Double>>,
    val weightChangeKg: Double?,
    val avgWeightKg: Double?,
    val currentStreak: Int,
    val bestStreak: Int,
    val activityCount: Int,
    val hasAnyData: Boolean,
) {
    val goalCompletion: Float get() = if (goalDaysCounted == 0) 0f else goalDaysMet.toFloat() / goalDaysCounted
    val stepChangeFraction: Double?
        get() = previousAvgDailySteps?.takeIf { it > 0 }?.let { (avgDailySteps - it).toDouble() / it }
}

object InsightsCalculator {

    fun period(range: InsightsRange, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        InsightsRange.DAY -> anchor to anchor
        InsightsRange.WEEK -> {
            val s = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            s to s.plusDays(6)
        }
        InsightsRange.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
        InsightsRange.YEAR -> anchor.withDayOfYear(1) to anchor.withDayOfYear(anchor.lengthOfYear())
    }

    fun shift(range: InsightsRange, anchor: LocalDate, by: Long): LocalDate = when (range) {
        InsightsRange.DAY -> anchor.plusDays(by)
        InsightsRange.WEEK -> anchor.plusWeeks(by)
        InsightsRange.MONTH -> anchor.plusMonths(by)
        InsightsRange.YEAR -> anchor.plusYears(by)
    }

    fun title(range: InsightsRange, start: LocalDate, end: LocalDate, today: LocalDate): String = when (range) {
        InsightsRange.DAY -> when (start) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> Formatters.mediumDate(start)
        }
        InsightsRange.WEEK -> "${Formatters.shortDate(start)} – ${Formatters.shortDate(end)}"
        InsightsRange.MONTH -> "${start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${start.year}"
        InsightsRange.YEAR -> start.year.toString()
    }

    fun compute(
        range: InsightsRange,
        anchor: LocalDate,
        today: LocalDate,
        steps: List<DailySteps>,
        weights: List<WeightEntry>,
        activities: List<ActivityEntry>,
        heightCm: Double?,
    ): InsightsData {
        val (start, end) = period(range, anchor)
        val stepsByDate = steps.associateBy { it.date }
        val activitiesByDate = activities.groupBy { it.date }
        val firstDataDate = (steps.filter { it.total > 0 }.map { it.date } + weights.map { it.date } + activities.map { it.date }).minOrNull()
        val latestWeight = weights.maxWithOrNull(compareBy({ it.date }, { it.createdAt }))?.weightKg

        fun countedDays(s: LocalDate, e: LocalDate): List<LocalDate> {
            if (firstDataDate == null) return emptyList()
            val from = maxOf(s, firstDataDate)
            val to = minOf(e, today)
            if (from > to) return emptyList()
            return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        }

        fun stepsOn(d: LocalDate) = stepsByDate[d]?.total ?: 0

        val counted = countedDays(start, end)
        val totalSteps = counted.sumOf { stepsOn(it).toLong() }
        val avg = if (counted.isEmpty()) 0 else (totalSteps.toDouble() / counted.size).roundToInt()

        val prevAnchor = shift(range, anchor, -1)
        val (ps, pe) = period(range, prevAnchor)
        val prevCounted = countedDays(ps, pe)
        val prevAvg = if (prevCounted.isEmpty()) null else
            (prevCounted.sumOf { stepsOn(it).toLong() }.toDouble() / prevCounted.size).roundToInt()

        var distance = 0.0
        var calories = 0
        var minutes = 0
        counted.forEach { d ->
            val s = DailySummaryCalculator.summarize(stepsOn(d), activitiesByDate[d].orEmpty(), heightCm, latestWeight)
            distance += s.distanceKm
            calories += s.caloriesKcal
            minutes += s.activeMinutes
        }

        val goalMet = counted.count { stepsByDate[it]?.goalMet == true }

        val bars: List<ChartBar> = when (range) {
            InsightsRange.DAY -> {
                val hourly = stepsByDate[start]?.hourly ?: List(24) { 0 }
                val nowHour = if (start == today) java.time.LocalTime.now().hour else -1
                hourly.mapIndexed { h, v -> ChartBar(Formatters.hourLabel(h), v.toFloat(), h == nowHour) }
            }
            InsightsRange.WEEK -> (0L..6L).map { i ->
                val d = start.plusDays(i)
                ChartBar(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), stepsOn(d).toFloat(), d == today)
            }
            InsightsRange.MONTH -> (0 until start.lengthOfMonth()).map { i ->
                val d = start.plusDays(i.toLong())
                ChartBar(d.dayOfMonth.toString(), stepsOn(d).toFloat(), d == today)
            }
            InsightsRange.YEAR -> (1..12).map { m ->
                val ms = start.withMonth(m).withDayOfMonth(1)
                val days = countedDays(ms, ms.withDayOfMonth(ms.lengthOfMonth()))
                val monthAvg = if (days.isEmpty()) 0f else days.sumOf { stepsOn(it) }.toFloat() / days.size
                ChartBar(ms.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), monthAvg, today.year == start.year && today.monthValue == m)
            }
        }

        val weightsInRange = weights.filter { it.date in start..end }
            .sortedWith(compareBy({ it.date }, { it.createdAt }))
        val weightPoints = weightsInRange.map { it.date to it.weightKg }
        val weightChange = if (weightsInRange.size >= 2) weightsInRange.last().weightKg - weightsInRange.first().weightKg else null

        val goalDays = steps.filter { it.goalMet }.map { it.date }.toSet()

        return InsightsData(
            range = range,
            start = start,
            end = end,
            title = title(range, start, end, today),
            bars = bars,
            totalSteps = totalSteps,
            avgDailySteps = avg,
            previousAvgDailySteps = prevAvg,
            totalDistanceKm = distance,
            totalCalories = calories,
            totalActiveMinutes = minutes,
            goalDaysMet = goalMet,
            goalDaysCounted = counted.size,
            weightPoints = weightPoints,
            weightChangeKg = weightChange,
            avgWeightKg = weightsInRange.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average(),
            currentStreak = StreakCalculator.currentStreak(goalDays, today),
            bestStreak = StreakCalculator.longestStreak(goalDays),
            activityCount = activities.count { it.date in start..end },
            hasAnyData = totalSteps > 0 || weightsInRange.isNotEmpty() || activities.any { it.date in start..end },
        )
    }
}
