package com.wefit.app.ui.steps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.currentDateFlow
import com.wefit.app.domain.calc.DailySummary
import com.wefit.app.domain.calc.DailySummaryCalculator
import com.wefit.app.domain.calc.InsightsCalculator
import com.wefit.app.domain.calc.InsightsData
import com.wefit.app.domain.calc.InsightsRange
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.DailySteps
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.step.StepTracker
import com.wefit.app.step.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StepTrackerUiState(
    val loading: Boolean = true,
    val range: InsightsRange = InsightsRange.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val units: UnitSystem = UnitSystem.METRIC,
    val goals: FitnessGoals = FitnessGoals(),
    val data: InsightsData? = null,
    val day: DailySteps? = null,
    val daySummary: DailySummary? = null,
    val activities: List<ActivityEntry> = emptyList(),
    val weightKg: Double? = null,
    val tracking: TrackingStatus = TrackingStatus(),
) {
    val canGoForward: Boolean get() = InsightsCalculator.period(range, anchor).second < today
}

private data class Selection(val range: InsightsRange = InsightsRange.DAY, val anchor: LocalDate? = null)

@HiltViewModel
class StepTrackerViewModel @Inject constructor(
    preferences: PreferencesRepository,
    profiles: ProfileRepository,
    goals: GoalRepository,
    weights: WeightRepository,
    private val steps: StepRepository,
    private val activities: ActivityRepository,
    private val tracker: StepTracker,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    val state: StateFlow<StepTrackerUiState> = combine(
        combine(preferences.preferences, profiles.profile, goals.goals, weights.entries) { p, pr, g, w -> Base(p.unitSystem, pr?.heightCm, g, w.firstOrNull()?.weightKg, w) },
        combine(steps.allDays, activities.all) { s, a -> s to a },
        selection,
        currentDateFlow(),
        tracker.status,
    ) { base, (allSteps, allActivities), sel, today, tracking ->
        val anchor = sel.anchor ?: today
        val data = InsightsCalculator.compute(sel.range, anchor, today, allSteps, base.weights, allActivities, base.heightCm)
        val day = if (sel.range == InsightsRange.DAY) allSteps.firstOrNull { it.date == anchor } else null
        val periodActivities = allActivities.filter { it.date in data.start..data.end }
        StepTrackerUiState(
            loading = false,
            range = sel.range,
            anchor = anchor,
            today = today,
            units = base.units,
            goals = base.goals,
            data = data,
            day = day,
            daySummary = if (sel.range == InsightsRange.DAY) {
                DailySummaryCalculator.summarize(day?.total ?: 0, periodActivities, base.heightCm, base.weightKg)
            } else null,
            activities = periodActivities,
            weightKg = base.weightKg,
            tracking = tracking,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepTrackerUiState())

    fun setRange(range: InsightsRange) = selection.update { it.copy(range = range) }

    fun shift(by: Long) = selection.update { sel ->
        val anchor = sel.anchor ?: LocalDate.now()
        val next = InsightsCalculator.shift(sel.range, anchor, by)
        sel.copy(anchor = if (next.isAfter(LocalDate.now())) LocalDate.now() else next)
    }

    /** Adds (positive) or removes (negative) manually entered steps for the selected day. */
    fun adjustSteps(date: LocalDate, delta: Int) {
        viewModelScope.launch { steps.adjustManualSteps(date, delta) }
    }

    fun deleteActivity(id: Long) {
        viewModelScope.launch { activities.delete(id) }
    }

    fun onPermissionResult() = tracker.onPermissionResult()
    fun refresh() {
        tracker.refreshStatus()
        viewModelScope.launch { tracker.syncOnce() }
    }

    private data class Base(
        val units: UnitSystem,
        val heightCm: Double?,
        val goals: FitnessGoals,
        val weightKg: Double?,
        val weights: List<com.wefit.app.domain.model.WeightEntry>,
    )
}
