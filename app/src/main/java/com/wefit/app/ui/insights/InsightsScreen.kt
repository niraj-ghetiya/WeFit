package com.wefit.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.Formatters
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.core.util.currentDateFlow
import com.wefit.app.domain.calc.InsightsCalculator
import com.wefit.app.domain.calc.InsightsData
import com.wefit.app.domain.calc.InsightsRange
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.ui.components.BarChart
import com.wefit.app.ui.components.EmptyState
import com.wefit.app.ui.components.LineChart
import com.wefit.app.ui.components.PeriodNavigator
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SegmentedSelector
import com.wefit.app.ui.components.StatTile
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

data class InsightsUiState(
    val loading: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
    val goals: FitnessGoals = FitnessGoals(),
    val today: LocalDate = LocalDate.now(),
    val data: InsightsData? = null,
) {
    val canGoForward: Boolean get() = data != null && data.end < today
}

enum class InsightTone { POSITIVE, NEGATIVE, NEUTRAL }
data class KeyInsight(val text: String, val tone: InsightTone)

/** Turns computed numbers into short, factual statements. */
fun buildKeyInsights(d: InsightsData, units: UnitSystem, goals: FitnessGoals): List<KeyInsight> = buildList {
    val period = when (d.range) {
        InsightsRange.DAY -> "day"; InsightsRange.WEEK -> "week"; InsightsRange.MONTH -> "month"; InsightsRange.YEAR -> "year"
    }
    d.stepChangeFraction?.let { f ->
        val pct = (abs(f) * 100).toInt()
        when {
            pct == 0 -> add(KeyInsight("Same daily step average as the previous $period", InsightTone.NEUTRAL))
            f > 0 -> add(KeyInsight("+$pct% more steps per day than the previous $period", InsightTone.POSITIVE))
            else -> add(KeyInsight("$pct% fewer steps per day than the previous $period", InsightTone.NEGATIVE))
        }
    }
    d.weightChangeKg?.let { c ->
        val toward = goals.targetWeightKg?.let { t -> d.weightPoints.firstOrNull()?.second?.let { first -> (t < first) == (c < 0) } }
        add(KeyInsight("${Formatters.weightChange(c, units)} weight change this $period",
            if (toward == true) InsightTone.POSITIVE else if (toward == false && abs(c) > 0.05) InsightTone.NEGATIVE else InsightTone.NEUTRAL))
    }
    if (d.goalDaysCounted > 0 && d.range != InsightsRange.DAY) {
        add(KeyInsight("Step goal reached on ${d.goalDaysMet} of ${d.goalDaysCounted} days",
            if (d.goalCompletion >= 0.5f) InsightTone.POSITIVE else InsightTone.NEUTRAL))
    }
    if (d.totalDistanceKm > 0.0) add(KeyInsight("Covered about ${Formatters.distance(d.totalDistanceKm, units)}", InsightTone.NEUTRAL))
    if (d.activityCount > 0) add(KeyInsight("${d.activityCount} ${if (d.activityCount == 1) "activity" else "activities"} logged", InsightTone.POSITIVE))
    if (d.currentStreak >= 2) add(KeyInsight("${d.currentStreak}-day step goal streak — keep it going!", InsightTone.POSITIVE))
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    preferences: PreferencesRepository,
    profiles: ProfileRepository,
    goals: GoalRepository,
    steps: StepRepository,
    weights: WeightRepository,
    activities: ActivityRepository,
) : ViewModel() {
    private val selection = MutableStateFlow<Pair<InsightsRange, LocalDate?>>(InsightsRange.WEEK to null)

    val state: StateFlow<InsightsUiState> = combine(
        combine(preferences.preferences, profiles.profile, goals.goals) { p, pr, g -> Triple(p.unitSystem, pr?.heightCm, g) },
        combine(steps.allDays, weights.entries, activities.all) { s, w, a -> Triple(s, w, a) },
        selection,
        currentDateFlow(),
    ) { (units, height, g), (s, w, a), (range, anchor), today ->
        InsightsUiState(false, units, g, today, InsightsCalculator.compute(range, anchor ?: today, today, s, w, a, height))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(range: InsightsRange) = selection.update { range to it.second }
    fun shift(by: Long) = selection.update { (range, anchor) ->
        val next = InsightsCalculator.shift(range, anchor ?: LocalDate.now(), by)
        range to if (next.isAfter(LocalDate.now())) LocalDate.now() else next
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.data
    val units = state.units

    Scaffold(topBar = { WeFitTopBar("Insights", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedSelector(InsightsRange.entries.map { it.label }, d?.range?.ordinal ?: 1, { viewModel.setRange(InsightsRange.entries[it]) })
            }
            item { PeriodNavigator(d?.title ?: "", { viewModel.shift(-1) }, { viewModel.shift(1) }, state.canGoForward) }
            if (d == null) return@LazyColumn
            if (!d.hasAnyData) {
                item {
                    EmptyState(Icons.Filled.Insights, "No data for this period",
                        "Walk with your phone, log your weight or add an activity, and your insights will appear here.")
                }
                return@LazyColumn
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrendTile(
                        "Avg steps / day", Formatters.number(d.avgDailySteps),
                        d.stepChangeFraction?.let { "${if (it >= 0) "▲" else "▼"} ${(abs(it) * 100).toInt()}%" },
                        d.stepChangeFraction?.let { if (it >= 0) WeFitTheme.colors.success else WeFitTheme.colors.danger },
                        Modifier.weight(1f),
                    )
                    TrendTile(
                        "Avg weight", d.avgWeightKg?.let { Formatters.weight(it, units) } ?: "—",
                        d.weightChangeKg?.let { Formatters.weightChange(it, units) }, null, Modifier.weight(1f),
                    )
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth()) {
                    SectionHeader(if (d.range == InsightsRange.YEAR) "Average daily steps by month" else "Step trend")
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        d.bars,
                        goalValue = if (d.range == InsightsRange.DAY) null else state.goals.dailyStepGoal.toFloat(),
                        labelEvery = when (d.range) { InsightsRange.DAY -> 6; InsightsRange.MONTH -> 5; else -> 1 },
                        description = "Steps for ${d.title}",
                    )
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth()) {
                    SectionHeader("Weight change")
                    if (d.weightPoints.isEmpty()) {
                        Text("No weight entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        LineChart(
                            d.weightPoints.map { UnitConverter.weightToDisplay(it.second, units).toFloat() },
                            d.weightPoints.map { Formatters.shortDate(it.first) },
                            targetValue = state.goals.targetWeightKg?.let { UnitConverter.weightToDisplay(it, units).toFloat() },
                            height = 160.dp,
                            description = "Weight for ${d.title}",
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Filled.Place, Formatters.distance(d.totalDistanceKm, units), "Distance", Modifier.weight(1f))
                    StatTile(Icons.Filled.LocalFireDepartment, Formatters.number(d.totalCalories), "kcal (est.)", Modifier.weight(1f), tint = WeFitTheme.colors.accent)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Filled.Timer, Formatters.duration(d.totalActiveMinutes), "Active time", Modifier.weight(1f))
                    StatTile(Icons.Filled.CheckCircle, Formatters.percent(d.goalCompletion.toDouble()), "Goal completion", Modifier.weight(1f), tint = WeFitTheme.colors.success)
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Whatshot, null, tint = WeFitTheme.colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Fitness streak", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${d.currentStreak} days · best ${d.bestStreak}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            val insights = buildKeyInsights(d, units, state.goals)
            if (insights.isNotEmpty()) {
                item {
                    WeFitCard(Modifier.fillMaxWidth()) {
                        SectionHeader("Key insights")
                        insights.forEach { insight ->
                            val (icon, tint) = insightIcon(insight.tone)
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(insight.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun insightIcon(tone: InsightTone): Pair<ImageVector, Color> = when (tone) {
    InsightTone.POSITIVE -> Icons.AutoMirrored.Filled.TrendingUp to WeFitTheme.colors.success
    InsightTone.NEGATIVE -> Icons.AutoMirrored.Filled.TrendingDown to WeFitTheme.colors.danger
    InsightTone.NEUTRAL -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.primary
}

@Composable
private fun TrendTile(label: String, value: String, change: String?, changeColor: Color?, modifier: Modifier = Modifier) {
    WeFitCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            change?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = changeColor ?: MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
