package com.wefit.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.currentDateFlow
import com.wefit.app.domain.calc.DailySummary
import com.wefit.app.domain.calc.DailySummaryCalculator
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.step.StepTracker
import com.wefit.app.step.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class WeightComparison(val changeKg: Double, val sinceStart: Boolean)

data class HomeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val today: LocalDate = LocalDate.now(),
    val units: UnitSystem = UnitSystem.METRIC,
    val steps: Int = 0,
    val manualSteps: Int = 0,
    val goals: FitnessGoals = FitnessGoals(),
    val summary: DailySummary = DailySummary(0, 0.0, 0, 0, false),
    val currentWeight: WeightEntry? = null,
    val weightComparison: WeightComparison? = null,
    val todayActivities: List<ActivityEntry> = emptyList(),
    val tracking: TrackingStatus = TrackingStatus(),
    val profileIncomplete: Boolean = false,
)

/** Compares the latest entry with the one ~30 days earlier, falling back to the first entry. */
fun weightComparison(entriesNewestFirst: List<WeightEntry>): WeightComparison? {
    val latest = entriesNewestFirst.firstOrNull() ?: return null
    val older = entriesNewestFirst.drop(1)
    if (older.isEmpty()) return null
    val monthAgo = older.firstOrNull { !it.date.isAfter(latest.date.minusDays(30)) }
    return if (monthAgo != null) WeightComparison(latest.weightKg - monthAgo.weightKg, sinceStart = false)
    else WeightComparison(latest.weightKg - older.last().weightKg, sinceStart = true)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    preferences: PreferencesRepository,
    profiles: ProfileRepository,
    goals: GoalRepository,
    weights: WeightRepository,
    steps: StepRepository,
    activities: ActivityRepository,
    private val tracker: StepTracker,
) : ViewModel() {

    private val todayData = currentDateFlow().flatMapLatest { date ->
        combine(steps.observeDay(date), activities.observeByDate(date)) { s, a -> Triple(date, s, a) }
    }

    val state: StateFlow<HomeUiState> = combine(
        combine(preferences.preferences, profiles.profile, goals.goals) { p, pr, g -> Triple(p, pr, g) },
        weights.entries,
        todayData,
        tracker.status,
    ) { (prefs, profile, goal), weightList, (date, daySteps, dayActivities), tracking ->
        val latest = weightList.firstOrNull()
        val total = daySteps?.total ?: 0
        HomeUiState(
            loading = false,
            name = profile?.name.orEmpty(),
            today = date,
            units = prefs.unitSystem,
            steps = total,
            manualSteps = daySteps?.manualSteps ?: 0,
            goals = goal,
            summary = DailySummaryCalculator.summarize(total, dayActivities, profile?.heightCm, latest?.weightKg),
            currentWeight = latest,
            weightComparison = weightComparison(weightList),
            todayActivities = dayActivities,
            tracking = tracking,
            profileIncomplete = profile?.heightCm == null || latest == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onPermissionResult() = tracker.onPermissionResult()
    fun refreshTracking() = tracker.refreshStatus()
}
