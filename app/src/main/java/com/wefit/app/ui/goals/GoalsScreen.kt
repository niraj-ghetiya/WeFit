package com.wefit.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.Formatters
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.core.util.currentDateFlow
import com.wefit.app.domain.calc.AchievementEvaluator
import com.wefit.app.domain.calc.DailySummaryCalculator
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.ui.components.IconBadge
import com.wefit.app.ui.components.NavRow
import com.wefit.app.ui.components.NumberField
import com.wefit.app.ui.components.ProgressRing
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.setup.ProfileValidator
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class GoalsUiState(
    val units: UnitSystem = UnitSystem.METRIC,
    val goals: FitnessGoals = FitnessGoals(),
    val todaySteps: Int = 0,
    val startWeightKg: Double? = null,
    val currentWeightKg: Double? = null,
    val weekActiveMinutes: Int = 0,
) {
    val stepProgress get() = FitnessCalculator.goalProgress(todaySteps, goals.dailyStepGoal)
    val weightProgress get() = FitnessCalculator.weightGoalProgress(startWeightKg, currentWeightKg, goals.targetWeightKg)
    val weeklyProgress get() = FitnessCalculator.goalProgress(weekActiveMinutes, goals.weeklyActiveMinutesGoal)
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    preferences: PreferencesRepository,
    profiles: ProfileRepository,
    private val goals: GoalRepository,
    weights: WeightRepository,
    private val steps: StepRepository,
    activities: ActivityRepository,
) : ViewModel() {
    val state: StateFlow<GoalsUiState> = combine(
        combine(preferences.preferences, profiles.profile, goals.goals) { p, pr, g -> Triple(p.unitSystem, pr?.heightCm, g) },
        weights.entries,
        steps.allDays,
        activities.all,
        currentDateFlow(),
    ) { (units, height, g), w, days, acts, today ->
        val weekStart = AchievementEvaluator.weekStart(today)
        val latest = w.firstOrNull()?.weightKg
        val minutes = generateSequence(weekStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.sumOf { d ->
            DailySummaryCalculator.summarize(days.firstOrNull { it.date == d }?.total ?: 0, acts.filter { it.date == d }, height, latest).activeMinutes
        }
        GoalsUiState(units, g, days.firstOrNull { it.date == today }?.total ?: 0, w.lastOrNull()?.weightKg, latest, minutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun save(transform: (FitnessGoals) -> FitnessGoals) {
        viewModelScope.launch {
            val updated = transform(goals.get())
            goals.save(updated)
            steps.syncGoalForDay(LocalDate.now(), updated.dailyStepGoal)
        }
    }
}

private enum class GoalEdit { STEPS, WEIGHT, WEEKLY }

@Composable
fun GoalsScreen(onNavigate: (String) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalEdit?>(null) }
    val units = state.units

    Scaffold(topBar = { WeFitTopBar("Goals") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GoalCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Daily Step Goal", "${Formatters.number(state.goals.dailyStepGoal)} steps") { editing = GoalEdit.STEPS }
            }
            item {
                GoalCard(Icons.Filled.MonitorWeight, "Target Weight", state.goals.targetWeightKg?.let { Formatters.weight(it, units) } ?: "Not set") { editing = GoalEdit.WEIGHT }
            }
            item {
                GoalCard(Icons.Filled.Timer, "Weekly Activity Goal", "${state.goals.weeklyActiveMinutesGoal} active min / week") { editing = GoalEdit.WEEKLY }
            }
            item { SectionHeader("Progress") }
            item {
                WeFitCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        GoalRing(state.stepProgress, "Steps today", "${Formatters.number(state.todaySteps)} / ${Formatters.number(state.goals.dailyStepGoal)}", useAccent = true)
                        GoalRing(
                            state.weightProgress, "Weight",
                            if (state.goals.targetWeightKg == null) "Set a target" else state.currentWeightKg?.let { Formatters.weight(it, units) } ?: "Log weight",
                        )
                        GoalRing(state.weeklyProgress, "This week", "${state.weekActiveMinutes} / ${state.goals.weeklyActiveMinutesGoal} min")
                    }
                }
            }
            item {
                val allDone = state.stepProgress >= 1f && state.weeklyProgress >= 1f
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WeFitTheme.colors.accentContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = WeFitTheme.colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (allDone) "Goals smashed!" else if (state.stepProgress >= 0.5f) "You're on track!" else "Every step counts",
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (allDone) "You've hit today's steps and this week's activity goal." else "Keep going to reach your goals.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    NavRow(Icons.Filled.Notifications, "Reminder preferences", "Weight, steps, activity and weekly summary") {
                        onNavigate(Routes.REMINDERS)
                    }
                }
            }
        }
    }

    when (editing) {
        GoalEdit.STEPS -> EditValueDialog(
            title = "Daily step goal", initial = state.goals.dailyStepGoal.toString(), suffix = "steps", decimal = false,
            validate = { v -> if (v.toIntOrNull() in 500..100_000) null else "Enter 500 to 100,000 steps" },
            onSave = { v -> viewModel.save { it.copy(dailyStepGoal = v.toInt()) } },
            onDismiss = { editing = null },
        )
        GoalEdit.WEIGHT -> EditValueDialog(
            title = "Target weight",
            initial = state.goals.targetWeightKg?.let { String.format(Locale.US, "%.1f", UnitConverter.weightToDisplay(it, units)) }.orEmpty(),
            suffix = UnitConverter.weightUnit(units), decimal = true,
            validate = { v ->
                val kg = v.toDoubleOrNull()?.let { UnitConverter.weightFromDisplay(it, units) }
                if (kg != null && kg in ProfileValidator.MIN_WEIGHT_KG..ProfileValidator.MAX_WEIGHT_KG) null else "Enter a realistic weight"
            },
            onSave = { v -> viewModel.save { it.copy(targetWeightKg = UnitConverter.weightFromDisplay(v.toDouble(), units)) } },
            onClear = if (state.goals.targetWeightKg != null) ({ viewModel.save { it.copy(targetWeightKg = null) } }) else null,
            onDismiss = { editing = null },
        )
        GoalEdit.WEEKLY -> EditValueDialog(
            title = "Weekly active minutes", initial = state.goals.weeklyActiveMinutesGoal.toString(), suffix = "min", decimal = false,
            validate = { v -> if (v.toIntOrNull() in 10..3_000) null else "Enter 10 to 3,000 minutes" },
            onSave = { v -> viewModel.save { it.copy(weeklyActiveMinutesGoal = v.toInt()) } },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun GoalCard(icon: ImageVector, title: String, value: String, onEdit: () -> Unit) {
    WeFitCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.Edit, contentDescription = "Edit $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GoalRing(progress: Float, label: String, detail: String, useAccent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        ProgressRing(
            progress, size = 84.dp, stroke = 9.dp,
            brush = if (useAccent) WeFitTheme.colors.accentGradient else WeFitTheme.colors.buttonGradient,
        ) { Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun EditValueDialog(
    title: String,
    initial: String,
    suffix: String,
    decimal: Boolean,
    validate: (String) -> String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumberField(value, { value = it; error = null }, title, suffix = suffix, decimal = decimal, error = error) },
        confirmButton = {
            TextButton(onClick = {
                val e = validate(value)
                if (e != null) error = e else { onSave(value); onDismiss() }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = { onClear(); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
